package org.example.backendweride.platform.garage.interfaces.internal;

import org.example.backendweride.platform.garage.domain.model.aggregates.Vehicle;
import org.example.backendweride.platform.garage.infrastructure.persistence.jpa.VehicleRepository;
import org.example.backendweride.platform.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/vehicles")
public class InternalVehiclesController {
    private final VehicleRepository vehicles;

    public InternalVehiclesController(VehicleRepository vehicles) {
        this.vehicles = vehicles;
    }

    private Vehicle find(Long id) {
        return vehicles.findById(id)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Vehicle not found"));
    }

    @GetMapping("/{id}")
    public Vehicle get(@PathVariable Long id, Authentication auth) {
        CurrentUser.requireService(auth, "booking", "ride");
        return find(id);
    }

    public record Move(String locationId) {}

    @PutMapping("/{id}/location")
    @Transactional
    public void move(@PathVariable Long id, @RequestBody Move body, Authentication auth) {
        CurrentUser.requireService(auth, "booking");
        if (body.locationId() == null || body.locationId().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var v =
                vehicles.lock(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        v.moveTo(body.locationId());
        vehicles.save(v);
    }

    @PutMapping("/{id}/maintenance")
    @Transactional
    public void maintenance(@PathVariable Long id, Authentication auth) {
        CurrentUser.requireService(auth, "ride");
        var v =
                vehicles.lock(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        v.markForMaintenance();
        vehicles.save(v);
    }
}
