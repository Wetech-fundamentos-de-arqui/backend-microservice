package org.example.backendweride.platform.trip.domain.services.commands;

import org.example.backendweride.platform.trip.domain.aggregates.Trip;
import org.example.backendweride.platform.trip.domain.commands.CreateTripCommand;

import java.util.Optional;

public interface TripCommandService {
    Optional<Trip> handle(CreateTripCommand command);

    void handle(Long id);
}
