package org.example.backendweride.platform.problemreports.infrastructure.persistence.jpa;

import org.example.backendweride.platform.problemreports.domain.model.aggregates.ProblemReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProblemReportRepository extends JpaRepository<ProblemReport, String> {
    List<ProblemReport> findAllByUserId(String userId);

    java.util.Optional<ProblemReport> findByUserIdAndRequestKey(String userId, String requestKey);

    java.util.List<ProblemReport> findTop50ByProcessingStatusOrderByReportDateAsc(
            String processingStatus);

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from ProblemReport r where r.id=:id")
    java.util.Optional<ProblemReport> lock(
            @org.springframework.data.repository.query.Param("id") String id);
}
