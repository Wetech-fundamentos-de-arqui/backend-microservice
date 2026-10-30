package org.example.backendweride.platform.notifications.interfaces.internal;

import jakarta.persistence.EntityManager;

import org.example.backendweride.platform.notifications.infrastructure.privacy.UserDataGuard;
import org.example.backendweride.platform.shared.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/users")
public class PersonalDataController {
    private final EntityManager entities;
    private final UserDataGuard guard;

    public PersonalDataController(EntityManager entities, UserDataGuard guard) {
        this.entities = entities;
        this.guard = guard;
    }

    @DeleteMapping("/{userId}/personal-data/{workflowId}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(
            @PathVariable String userId, @PathVariable String workflowId, Authentication auth) {
        CurrentUser.requireService(auth, "iam");
        guard.tombstone(userId, workflowId);
        entities.createQuery("select e from Notification e where e.userId=:id")
                .setParameter("id", userId)
                .getResultList()
                .forEach(entities::remove);
    }
}
