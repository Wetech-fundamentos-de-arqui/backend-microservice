package org.example.backendweride.platform.ride.application;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.example.backendweride.platform.ride.infrastructure.clients.RideDependencies;
import org.example.backendweride.platform.ride.infrastructure.outbox.OutboxService;
import org.example.backendweride.platform.ride.infrastructure.privacy.UserDataGuard;
import org.example.backendweride.platform.trip.domain.aggregates.Trip;
import org.example.backendweride.platform.trip.domain.valueobjects.RouteCoordinates;
import org.example.backendweride.platform.trip.infrastructure.persistence.jpa.TripRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.util.*;

@Service
public class RideWorkflow {
    public record Completion(
            Double totalCost,
            Double discount,
            Double distance,
            Integer duration,
            Double averageSpeed,
            Integer ratingScore,
            String ratingComment,
            List<RouteCoordinates> routeCoordinates,
            String routeSource) {}

    public record Result(
            String id,
            String bookingId,
            String userId,
            String vehicleId,
            String status,
            Date startDate,
            Date endDate,
            Double totalCost,
            Double distance,
            Integer duration,
            Double averageSpeed,
            List<RouteCoordinates> routeCoordinates,
            String routeSource) {}

    private final TripRepository trips;
    private final RideDependencies remote;
    private final UserDataGuard guard;
    private final OutboxService outbox;
    private final ObjectMapper json;

    public RideWorkflow(
            TripRepository trips,
            RideDependencies remote,
            UserDataGuard guard,
            OutboxService outbox,
            ObjectMapper json) {
        this.trips = trips;
        this.remote = remote;
        this.guard = guard;
        this.outbox = outbox;
        this.json = json;
    }

    private Result result(Trip t) {
        return new Result(
                t.getId().toString(),
                t.getBookingId(),
                t.getUserId(),
                t.getVehicleId(),
                t.getStatus(),
                t.getStartDate(),
                t.getEndDate(),
                t.getTotalCost(),
                t.getDistance(),
                t.getDuration(),
                t.getAverageSpeed(),
                t.getRouteCoordinates(),
                t.getRouteSource());
    }

    public Result get(String bookingId) {
        return result(
                trips.findByBookingId(bookingId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @Transactional
    public Result start(String id) {
        var b = remote.booking(id);
        if (!Set.of("starting", "in_progress").contains(b.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking is not starting");
        guard.assertWritable(b.userId());
        var old = trips.lockByBookingId(id);
        if (old.isPresent()) {
            if ("cancelled".equals(old.get().getStatus()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ride cancelled");
            return result(old.get());
        }
        var vehicle = remote.vehicle(b.vehicleId());
        if ("maintenance".equals(vehicle.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle in maintenance");
        var t =
                trips.saveAndFlush(
                        new Trip(
                                id,
                                b.userId(),
                                b.vehicleId(),
                                b.startLocationId(),
                                b.endLocationId(),
                                new Date()));
        return result(t);
    }

    @Transactional
    public Result complete(String id, Completion c) {
        if (c.totalCost() == null
                || c.distance() == null
                || c.duration() == null
                || c.averageSpeed() == null
                || !Double.isFinite(c.totalCost())
                || !Double.isFinite(c.distance())
                || !Double.isFinite(c.averageSpeed())
                || c.totalCost() < 0
                || c.distance() < 0
                || c.duration() < 0
                || c.averageSpeed() < 0
                || (c.routeCoordinates() != null
                        && (c.routeCoordinates().size() > 5000
                                || c.routeCoordinates().stream().anyMatch(Objects::isNull)))
                || (c.routeSource() != null
                        && !Set.of("gps", "simulated").contains(c.routeSource())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ride result");
        var b = remote.booking(id);
        if (!Set.of("completing", "completed").contains(b.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking is not completing");
        guard.assertWritable(b.userId());
        var t =
                trips.lockByBookingId(id)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Ride session not found"));
        String hash;
        try {
            hash =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(json.writeValueAsBytes(c)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if ("completed".equals(t.getStatus())) {
            if (!hash.equals(t.getCompletionHash()))
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Completion payload changed");
            return result(t);
        }
        if (!"in_progress".equals(t.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ride is not in progress");
        t.complete(
                c.totalCost(),
                c.distance(),
                c.duration(),
                c.averageSpeed(),
                c.routeCoordinates() == null ? List.of() : List.copyOf(c.routeCoordinates()),
                c.routeSource(),
                hash);
        trips.save(t);
        outbox.enqueue(
                "ride.completed.v1",
                t.getUserId(),
                "Recorrido guardado",
                "El recorrido de tu viaje está disponible en el historial.",
                id,
                "ride");
        return result(t);
    }

    @Transactional
    public Result cancel(String id) {
        var b = remote.booking(id);
        if (!Set.of("cancelling", "cancelled").contains(b.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking is not cancelling");
        guard.assertWritable(b.userId());
        var t =
                trips.lockByBookingId(id)
                        .orElseGet(
                                () ->
                                        new Trip(
                                                id,
                                                b.userId(),
                                                b.vehicleId(),
                                                b.startLocationId(),
                                                b.endLocationId(),
                                                new Date()));
        if ("completed".equals(t.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ride already completed");
        t.cancel();
        return result(trips.saveAndFlush(t));
    }
}
