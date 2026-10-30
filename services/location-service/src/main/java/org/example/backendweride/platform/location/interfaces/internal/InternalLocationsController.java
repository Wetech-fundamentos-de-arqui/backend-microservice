package org.example.backendweride.platform.location.interfaces.internal;

import org.example.backendweride.platform.location.domain.model.aggregates.Location;
import org.example.backendweride.platform.location.infrastructure.persistence.jpa.LocationRepository;
import org.example.backendweride.platform.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/locations")
public class InternalLocationsController {
    private final LocationRepository locations;

    public InternalLocationsController(LocationRepository locations) {
        this.locations = locations;
    }

    @GetMapping
    public List<Location> all(Authentication auth) {
        CurrentUser.requireService(auth, "booking");
        return locations.findAll();
    }

    @GetMapping("/{id}")
    public Location get(@PathVariable Long id, Authentication auth) {
        CurrentUser.requireService(auth, "booking");
        return locations
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
