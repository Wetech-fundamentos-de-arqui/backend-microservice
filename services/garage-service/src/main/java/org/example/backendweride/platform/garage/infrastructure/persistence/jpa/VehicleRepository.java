package org.example.backendweride.platform.garage.infrastructure.persistence.jpa;

import org.example.backendweride.platform.garage.domain.model.aggregates.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select v from Vehicle v where v.id=:id")
    java.util.Optional<Vehicle> lock(
            @org.springframework.data.repository.query.Param("id") Long id);
}
