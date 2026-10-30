package org.example.backendweride.platform.ride.infrastructure.outbox;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findTop50ByStateAndNextAttemptAtLessThanEqualOrderByOccurredAtAsc(
            String state, Instant now);

    List<OutboxEvent> findTop100ByStateOrderByOccurredAtAsc(String state);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboxEvent e where e.eventId=:id")
    Optional<OutboxEvent> lock(@Param("id") String id);
}
