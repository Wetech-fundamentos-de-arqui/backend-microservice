package org.example.backendweride.platform.trip.domain.commands;

import org.example.backendweride.platform.trip.domain.valueobjects.RouteCoordinates;

import java.util.Date;
import java.util.List;

public record CreateTripCommand(
        String bookingId,
        String userId,
        String vehicleId,
        String startLocationId,
        String endLocationId,
        String route,
        List<RouteCoordinates> routeCoordinates,
        Date startDate,
        Date endDate,
        int duration,
        double distance,
        double averageSpeed,
        double maxSpeed,
        double totalCost,
        double carbonSaved,
        int caloriesBurned,
        String weather,
        int temperature,
        String status,
        List<String> incidentReports,
        List<String> photos) {}
