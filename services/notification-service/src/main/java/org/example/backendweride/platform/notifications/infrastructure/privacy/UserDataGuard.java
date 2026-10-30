package org.example.backendweride.platform.notifications.infrastructure.privacy;

import jakarta.persistence.EntityManager;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Local per-user lock and tombstone. All mutations join the caller's local transaction. */
@Service
public class UserDataGuard {
    private final EntityManager entities;

    public UserDataGuard(EntityManager entities) {
        this.entities = entities;
    }

    public record State(boolean deleted, boolean pending) {}

    @Transactional
    public State lock(String userId) {
        if (userId == null || userId.isBlank() || userId.length() > 254)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user id");
        entities.createNativeQuery(
                        "INSERT INTO user_guards (user_id,deleted,deletion_pending) VALUES"
                            + " (:id,false,false) ON DUPLICATE KEY UPDATE user_id=user_id")
                .setParameter("id", userId)
                .executeUpdate();
        Object[] row =
                (Object[])
                        entities.createNativeQuery(
                                        "SELECT deleted+0,deletion_pending+0 FROM user_guards WHERE"
                                            + " user_id=:id FOR UPDATE")
                                .setParameter("id", userId)
                                .getSingleResult();
        return new State(((Number) row[0]).intValue() != 0, ((Number) row[1]).intValue() != 0);
    }

    @Transactional
    public void assertWritable(String userId) {
        State s = lock(userId);
        if (s.deleted()) throw new ResponseStatusException(HttpStatus.GONE, "USER_DELETED");
        if (s.pending()) throw new ResponseStatusException(HttpStatus.CONFLICT, "DELETION_PENDING");
    }

    @Transactional
    public void block(String userId) {
        lock(userId);
        entities.createNativeQuery("UPDATE user_guards SET deletion_pending=true WHERE user_id=:id")
                .setParameter("id", userId)
                .executeUpdate();
    }

    @Transactional
    public void unblock(String userId) {
        lock(userId);
        entities.createNativeQuery(
                        "UPDATE user_guards SET deletion_pending=false WHERE user_id=:id AND"
                            + " deleted=false")
                .setParameter("id", userId)
                .executeUpdate();
    }

    @Transactional
    public void tombstone(String userId, String workflowId) {
        lock(userId);
        entities.createNativeQuery(
                        "UPDATE user_guards SET"
                            + " deleted=true,deletion_pending=true,workflow_id=:workflow WHERE"
                            + " user_id=:id")
                .setParameter("id", userId)
                .setParameter("workflow", workflowId)
                .executeUpdate();
    }
}
