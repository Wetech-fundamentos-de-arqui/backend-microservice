package org.example.backendweride.platform.trip.application.internal.commands;

import org.example.backendweride.platform.ride.infrastructure.clients.RideDependencies;
import org.example.backendweride.platform.ride.infrastructure.privacy.UserDataGuard;
import org.example.backendweride.platform.trip.domain.aggregates.Trip;
import org.example.backendweride.platform.trip.domain.commands.CreateTripCommand;
import org.example.backendweride.platform.trip.domain.services.commands.TripCommandService;
import org.example.backendweride.platform.trip.infrastructure.persistence.jpa.TripRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@Transactional
public class TripCommandServiceImpl implements TripCommandService {
    private final TripRepository trips;
    private final RideDependencies remote;
    private final UserDataGuard guard;

    public TripCommandServiceImpl(
            TripRepository trips, RideDependencies remote, UserDataGuard guard) {
        this.trips = trips;
        this.remote = remote;
        this.guard = guard;
    }

    public Optional<Trip> handle(CreateTripCommand c) {
        guard.assertWritable(c.userId());
        var b = remote.booking(c.bookingId());
        if (!c.userId().equals(b.userId()) || !Objects.equals(c.vehicleId(), b.vehicleId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var old = trips.findByBookingId(c.bookingId());
        if (old.isPresent()) return old;
        if (!"completed".equals(b.status()))
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Use Booking to start and complete a ride");
        return Optional.of(trips.save(new Trip(c)));
    }

    public void handle(Long id) {
        var t =
                trips.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        guard.assertWritable(t.getUserId());
        if ("in_progress".equals(t.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ride still active");
        trips.delete(t);
    }
}
