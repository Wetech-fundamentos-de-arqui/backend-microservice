package org.example.backendweride.platform.trip.infrastructure.persistence.jpa;

import org.example.backendweride.platform.trip.domain.aggregates.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

    java.util.Optional<Trip> findByBookingId(String bookingId);

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "select t from Trip t where t.bookingId=:bookingId")
    java.util.Optional<Trip> lockByBookingId(
            @org.springframework.data.repository.query.Param("bookingId") String bookingId);
}
