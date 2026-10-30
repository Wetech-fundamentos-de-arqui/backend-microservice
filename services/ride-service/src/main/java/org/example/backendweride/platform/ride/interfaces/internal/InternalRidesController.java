package org.example.backendweride.platform.ride.interfaces.internal;

import org.example.backendweride.platform.ride.application.RideWorkflow;
import org.example.backendweride.platform.shared.security.CurrentUser;
import org.example.backendweride.platform.unlock.infrastructure.UnlockRequestRepository;
import org.example.backendweride.platform.unlock.interfaces.UnlockRequestResource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class InternalRidesController {
    private final RideWorkflow rides;
    private final UnlockRequestRepository unlocks;

    public InternalRidesController(RideWorkflow rides, UnlockRequestRepository unlocks) {
        this.rides = rides;
        this.unlocks = unlocks;
    }

    @PostMapping("/internal/v1/rides/{bookingId}/start")
    public RideWorkflow.Result start(@PathVariable String bookingId, Authentication auth) {
        CurrentUser.requireService(auth, "booking");
        return rides.start(bookingId);
    }

    @GetMapping("/internal/v1/rides/by-booking/{bookingId}")
    public RideWorkflow.Result get(@PathVariable String bookingId, Authentication auth) {
        CurrentUser.requireService(auth, "booking");
        return rides.get(bookingId);
    }

    @PostMapping("/internal/v1/rides/{bookingId}/complete")
    public RideWorkflow.Result complete(
            @PathVariable String bookingId,
            @RequestBody RideWorkflow.Completion result,
            Authentication auth) {
        CurrentUser.requireService(auth, "booking");
        return rides.complete(bookingId, result);
    }

    @PostMapping("/internal/v1/rides/{bookingId}/cancel")
    public RideWorkflow.Result cancel(@PathVariable String bookingId, Authentication auth) {
        CurrentUser.requireService(auth, "booking");
        return rides.cancel(bookingId);
    }

    @GetMapping("/internal/v1/bookings/{bookingId}/unlock")
    public UnlockRequestResource unlock(@PathVariable String bookingId, Authentication auth) {
        CurrentUser.requireService(auth, "booking");
        return unlocks.findFirstByBookingIdOrderByRequestedAtDesc(bookingId)
                .map(UnlockRequestResource::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
