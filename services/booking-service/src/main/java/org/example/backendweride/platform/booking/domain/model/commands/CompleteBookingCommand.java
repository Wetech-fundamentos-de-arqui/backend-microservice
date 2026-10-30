package org.example.backendweride.platform.booking.domain.model.commands;

import org.example.backendweride.platform.booking.domain.model.valueobjects.TripRouteCoordinate;

import java.util.List;

public record CompleteBookingCommand(
        String bookingId,
        Double totalCost,
        Double discount,
        Double distance,
        Integer duration,
        Double averageSpeed,
        Integer ratingScore,
        String ratingComment,
        List<TripRouteCoordinate> routeCoordinates,
        String routeSource) {}
