package org.example.backendweride.platform.trip.interfaces;

import org.example.backendweride.platform.shared.security.CurrentUser;
import org.example.backendweride.platform.trip.infrastructure.persistence.jpa.TripRepository;
import org.example.backendweride.platform.trip.interfaces.resources.TripResource;
import org.example.backendweride.platform.trip.interfaces.transform.TripResourceFromEntityAssembler;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class TripDetailController {
    private final TripRepository trips;

    public TripDetailController(TripRepository trips) {
        this.trips = trips;
    }

    @GetMapping("/api/v1/trips/{id}")
    public TripResource get(@PathVariable Long id, Authentication auth) {
        var t =
                trips.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        CurrentUser.requireSelf(auth, t.getUserId());
        return TripResourceFromEntityAssembler.toResource(t);
    }
}
