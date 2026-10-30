package org.example.backendweride.platform.location.interfaces;

import org.example.backendweride.platform.location.domain.model.aggregates.Location;
import org.example.backendweride.platform.location.infrastructure.persistence.jpa.LocationRepository;
import org.example.backendweride.platform.location.interfaces.resources.CreateLocationResource;
import org.example.backendweride.platform.location.interfaces.transform.CreateLocationCommandFromResourceAssembler;
import org.example.backendweride.platform.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class LocationCrudController {
    private final LocationRepository locations;

    public LocationCrudController(LocationRepository locations) {
        this.locations = locations;
    }

    @GetMapping("/api/v1/location/{id}")
    public Location get(@PathVariable Long id) {
        return locations
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PatchMapping("/api/v1/location/{id}")
    public Location update(
            @PathVariable Long id,
            @RequestBody CreateLocationResource resource,
            Authentication authentication) {
        CurrentUser.requireAdmin(authentication);
        // The update replaces every field, so an incomplete body would blank the station.
        if (resource.name() == null || resource.name().isBlank() || resource.coordinates() == null)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Station name and coordinates are required");
        return locations.save(
                get(id).update(
                                CreateLocationCommandFromResourceAssembler.toCommandFromResource(
                                        resource)));
    }

    @DeleteMapping("/api/v1/location/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        CurrentUser.requireAdmin(authentication);
        locations.delete(get(id));
    }
}
