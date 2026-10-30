package org.example.backendweride.platform.booking.interfaces.internal;

import jakarta.persistence.EntityManager;

import org.example.backendweride.platform.booking.application.commandservices.BookingCommandServiceImpl;
import org.example.backendweride.platform.booking.domain.model.aggregates.Booking;
import org.example.backendweride.platform.booking.infrastructure.persistence.jpa.BookingRepository;
import org.example.backendweride.platform.booking.infrastructure.privacy.UserDataGuard;
import org.example.backendweride.platform.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@RestController
public class InternalBookingsController {
    private final BookingRepository bookings;
    private final BookingCommandServiceImpl commands;
    private final UserDataGuard guard;
    private final EntityManager entities;

    public InternalBookingsController(
            BookingRepository bookings,
            BookingCommandServiceImpl commands,
            UserDataGuard guard,
            EntityManager entities) {
        this.bookings = bookings;
        this.commands = commands;
        this.guard = guard;
        this.entities = entities;
    }

    public record Snapshot(
            String id,
            String userId,
            String vehicleId,
            String startLocationId,
            String endLocationId,
            String status,
            Date startDate,
            Date endDate,
            Date actualStartDate,
            Date actualEndDate) {}

    @GetMapping("/internal/v1/bookings/{id}")
    public Snapshot get(@PathVariable String id, Authentication auth) {
        CurrentUser.requireService(auth, "ride");
        var b =
                bookings.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new Snapshot(
                b.getId(),
                b.getUserId(),
                b.getVehicleId(),
                b.getStartLocationId(),
                b.getEndLocationId(),
                b.getStatus(),
                b.getStartDate(),
                b.getEndDate(),
                b.getActualStartDate(),
                b.getActualEndDate());
    }

    @GetMapping("/internal/v1/vehicles/ratings")
    public Map<String, Double> ratings(Authentication auth) {
        CurrentUser.requireService(auth, "garage");
        var result = new HashMap<String, Double>();
        bookings.averageRatingsByVehicle()
                .forEach(r -> result.put((String) r[0], ((Number) r[1]).doubleValue()));
        return result;
    }

    @PostMapping("/internal/v1/users/{userId}/deletion-lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void prepare(@PathVariable String userId, Authentication auth) {
        CurrentUser.requireService(auth, "iam");
        commands.prepareDeletion(userId);
    }

    @DeleteMapping("/internal/v1/users/{userId}/deletion-lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void abort(@PathVariable String userId, Authentication auth) {
        CurrentUser.requireService(auth, "iam");
        guard.unblock(userId);
    }

    @DeleteMapping("/internal/v1/users/{userId}/personal-data/{workflowId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void erase(
            @PathVariable String userId, @PathVariable String workflowId, Authentication auth) {
        CurrentUser.requireService(auth, "iam");
        guard.lock(userId);
        var own = bookings.findAllByUserId(userId);
        if (own.stream().anyMatch(b -> Booking.ACTIVE_STATUSES.contains(b.getStatus())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ACTIVE_BOOKINGS");
        guard.tombstone(userId, workflowId);
        own.forEach(b -> b.anonymize("deleted-" + workflowId));
        bookings.saveAll(own);
        for (String entity : List.of("BookingDraft", "OutboxEvent"))
            entities.createQuery("select e from " + entity + " e where e.userId=:id")
                    .setParameter("id", userId)
                    .getResultList()
                    .forEach(entities::remove);
        entities.createNativeQuery("DELETE FROM booking_requests WHERE user_id=:id")
                .setParameter("id", userId)
                .executeUpdate();
    }

    public record Fault(String userId, String vehicleId, String reportId, Instant reportedAt) {}

    @PostMapping("/internal/v1/bookings/{id}/problem-report")
    public Map<String, Boolean> fault(
            @PathVariable String id, @RequestBody Fault fault, Authentication auth) {
        CurrentUser.requireService(auth, "ride");
        if (fault.reportId() == null
                || fault.reportedAt() == null
                || fault.reportedAt().isAfter(Instant.now().plusSeconds(5)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return Map.of(
                "chargeWaived",
                commands.reportFault(
                        id,
                        fault.userId(),
                        fault.vehicleId(),
                        fault.reportId(),
                        fault.reportedAt()));
    }
}
