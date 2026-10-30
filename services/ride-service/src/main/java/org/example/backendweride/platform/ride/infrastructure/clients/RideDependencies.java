package org.example.backendweride.platform.ride.infrastructure.clients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

@Component
public class RideDependencies {
    public record Booking(
            String id,
            String userId,
            String vehicleId,
            String startLocationId,
            String endLocationId,
            String status,
            Date startDate,
            Date endDate,
            Date actualStartDate,
            Date actualEndDate) {}

    public record Vehicle(String id, String status) {}

    public record FaultResult(boolean chargeWaived) {}

    private final RestClient booking, garage;

    public RideDependencies(
            RestClient.Builder builder,
            @Value("${weride.internal.targets.booking.url}") String bookingUrl,
            @Value("${weride.internal.targets.booking.secret}") String bookingSecret,
            @Value("${weride.internal.targets.garage.url}") String garageUrl,
            @Value("${weride.internal.targets.garage.secret}") String garageSecret) {
        booking =
                builder.clone()
                        .baseUrl(bookingUrl)
                        .defaultHeader("X-Internal-Token", bookingSecret)
                        .build();
        garage =
                builder.clone()
                        .baseUrl(garageUrl)
                        .defaultHeader("X-Internal-Token", garageSecret)
                        .build();
    }

    private <T> T call(String target, Supplier<T> action) {
        try {
            return action.get();
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            if (Set.of(400, 403, 404, 409, 410).contains(code))
                throw new ResponseStatusException(
                        HttpStatus.valueOf(code), target + " rejected the operation");
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, target + " unavailable");
        } catch (RestClientException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, target + " unavailable");
        }
    }

    public Booking booking(String id) {
        return call(
                "Booking",
                () ->
                        booking.get()
                                .uri("/internal/v1/bookings/{id}", id)
                                .retrieve()
                                .body(Booking.class));
    }

    public Vehicle vehicle(String id) {
        return call(
                "Garage",
                () ->
                        garage.get()
                                .uri("/internal/v1/vehicles/{id}", id)
                                .retrieve()
                                .body(Vehicle.class));
    }

    public boolean fault(
            String id, String userId, String vehicleId, String reportId, Instant reportedAt) {
        return call(
                        "Booking",
                        () ->
                                booking.post()
                                        .uri("/internal/v1/bookings/{id}/problem-report", id)
                                        .body(
                                                Map.of(
                                                        "userId",
                                                        userId,
                                                        "vehicleId",
                                                        vehicleId,
                                                        "reportId",
                                                        reportId,
                                                        "reportedAt",
                                                        reportedAt))
                                        .retrieve()
                                        .body(FaultResult.class))
                .chargeWaived();
    }

    public void maintenance(String vehicleId) {
        call(
                "Garage",
                () ->
                        garage.put()
                                .uri("/internal/v1/vehicles/{id}/maintenance", vehicleId)
                                .retrieve()
                                .toBodilessEntity());
    }
}
