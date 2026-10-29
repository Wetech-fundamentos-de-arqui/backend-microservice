package org.example.backendweride.platform.iam.domain.model.aggregates;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted ADR-008 deletion workflow of one account. Progress lives in its {@link DeletionStep}s.
 */
@Entity
@Table(name = "account_deletion_workflows")
@Getter
@NoArgsConstructor
public class AccountDeletionWorkflow {
    public enum Status { IN_PROGRESS, DELETED, ABORTED }

    @Id private String id;
    @Column(nullable = false) private Long accountId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    public AccountDeletionWorkflow(Long accountId) {
        this.id = UUID.randomUUID().toString();
        this.accountId = accountId;
        this.status = Status.IN_PROGRESS;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void markDeleted() { close(Status.DELETED); }
    public void markAborted() { close(Status.ABORTED); }

    private void close(Status closed) {
        status = closed;
        updatedAt = Instant.now();
    }
}
