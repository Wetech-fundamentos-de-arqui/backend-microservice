package org.example.backendweride.platform.ride.infrastructure.outbox;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor
public class OutboxEvent {
    @Id private String eventId;
    private String userId;
    private String eventType;

    @Column(columnDefinition = "LONGTEXT")
    private String payload;

    private String state;
    private int attempts;
    private Instant occurredAt;
    private Instant nextAttemptAt;

    @Column(length = 500)
    private String lastError;

    public OutboxEvent(String userId, String type, String payload) {
        this.eventId = UUID.randomUUID().toString();
        this.userId = userId;
        this.eventType = type;
        this.payload = payload;
        this.state = "PENDING";
        this.occurredAt = Instant.now();
        this.nextAttemptAt = occurredAt;
    }

    public void delivered() {
        state = "DELIVERED";
        lastError = null;
    }

    public void failed(String error) {
        attempts++;
        state = attempts >= 10 ? "FAILED" : "PENDING";
        lastError = error;
        nextAttemptAt = Instant.now().plusSeconds(Math.min(300, 2L << Math.min(attempts - 1, 8)));
    }

    public void replay() {
        state = "PENDING";
        attempts = 0;
        lastError = null;
        nextAttemptAt = Instant.now();
    }
}
