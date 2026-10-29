package org.example.backendweride.platform.iam.infrastructure.persistence.jpa.repositories;

import org.example.backendweride.platform.iam.domain.model.aggregates.AccountDeletionWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountDeletionWorkflowRepository extends JpaRepository<AccountDeletionWorkflow, String> {
    Optional<AccountDeletionWorkflow> findFirstByAccountIdAndStatus(Long accountId, AccountDeletionWorkflow.Status status);
    List<AccountDeletionWorkflow> findByStatus(AccountDeletionWorkflow.Status status);
}
