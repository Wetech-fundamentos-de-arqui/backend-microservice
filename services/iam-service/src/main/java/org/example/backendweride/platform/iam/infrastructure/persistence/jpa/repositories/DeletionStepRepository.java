package org.example.backendweride.platform.iam.infrastructure.persistence.jpa.repositories;

import org.example.backendweride.platform.iam.domain.model.aggregates.DeletionStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeletionStepRepository extends JpaRepository<DeletionStep, Long> {
    List<DeletionStep> findByWorkflowId(String workflowId);
}
