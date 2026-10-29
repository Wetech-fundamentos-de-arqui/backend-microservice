package org.example.backendweride.platform.iam.domain.model.aggregates;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * One call of a deletion workflow towards a target service (booking-lock, or personal-data of a service).
 */
@Entity
@Table(name = "deletion_steps")
@Getter
@NoArgsConstructor
public class DeletionStep {
    public enum Status { PENDING, DONE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String workflowId;
    @Column(nullable = false) private String target;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private Instant nextAttemptAt;
    @Column(nullable = false) private Instant updatedAt;

    public DeletionStep(String workflowId, String target, boolean done) {
        this.workflowId = workflowId;
        this.target = target;
        this.status = done ? Status.DONE : Status.PENDING;
        this.nextAttemptAt = Instant.now();
        this.updatedAt = nextAttemptAt;
    }

    public boolean isDone() { return status == Status.DONE; }
    public boolean isDue(Instant now) { return !isDone() && !nextAttemptAt.isAfter(now); }

    public void markDone() {
        status = Status.DONE;
        updatedAt = Instant.now();
    }

    public void retryAfter(Duration delay) {
        attempts++;
        updatedAt = Instant.now();
        nextAttemptAt = updatedAt.plus(delay);
    }
}
