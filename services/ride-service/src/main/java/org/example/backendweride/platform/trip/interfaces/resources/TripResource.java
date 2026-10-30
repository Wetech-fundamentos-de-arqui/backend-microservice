package org.example.backendweride.platform.trip.interfaces.resources;

import io.swagger.v3.oas.annotations.media.Schema;

import org.example.backendweride.platform.trip.domain.valueobjects.RouteCoordinates;

import java.util.Date;
import java.util.List;

@Schema(description = "A recorded trip with its route, metrics, costs and environmental data.")
public record TripResource(
        Long id,
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
