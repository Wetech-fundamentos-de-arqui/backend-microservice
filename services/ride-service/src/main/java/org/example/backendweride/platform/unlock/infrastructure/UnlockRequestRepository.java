package org.example.backendweride.platform.unlock.infrastructure;

import org.example.backendweride.platform.unlock.domain.model.UnlockRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnlockRequestRepository extends JpaRepository<UnlockRequest, String> {
    List<UnlockRequest> findAllByUserIdOrderByRequestedAtDesc(String userId);

    java.util.Optional<UnlockRequest> findFirstByBookingIdOrderByRequestedAtDesc(String bookingId);

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from UnlockRequest r where r.id=:id")
    java.util.Optional<UnlockRequest> lockById(
            @org.springframework.data.repository.query.Param("id") String id);
}
