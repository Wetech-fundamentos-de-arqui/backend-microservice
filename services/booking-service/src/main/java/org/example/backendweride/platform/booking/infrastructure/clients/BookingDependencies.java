package org.example.backendweride.platform.booking.infrastructure.clients;

import org.example.backendweride.platform.booking.domain.model.commands.CompleteBookingCommand;
import org.example.backendweride.platform.booking.domain.model.valueobjects.TripRouteCoordinate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Supplier;

@Component
public class BookingDependencies {
    public record Vehicle(
            String id,
            String brand,
            String model,
            String type,
            String image,
            String color,
            double pricePerMinute,
            Integer battery,
            String status,
            String location) {}

    public record Coordinates(double lat, double lng) {}

    public record Station(String id, String name, String address, Coordinates coordinates) {}

    public record Unlock(
            String id, String status, String accessCode, java.time.Instant codeExpiresAt) {}

    public record RideResult(
            String id,
            String bookingId,
            String userId,
            String vehicleId,
            String status,
            Date startDate,
            Date endDate,
            Double totalCost,
            Double distance,
            Integer duration,
            Double averageSpeed,
            List<TripRouteCoordinate> routeCoordinates,
            String routeSource) {}

    private final RestClient garage, location, ride;

    public BookingDependencies(
            RestClient.Builder builder,
            @Value("${weride.internal.targets.garage.url}") String garageUrl,
            @Value("${weride.internal.targets.garage.secret}") String garageSecret,
            @Value("${weride.internal.targets.location.url}") String locationUrl,
            @Value("${weride.internal.targets.location.secret}") String locationSecret,
            @Value("${weride.internal.targets.ride.url}") String rideUrl,
            @Value("${weride.internal.targets.ride.secret}") String rideSecret) {
        garage =
                builder.clone()
                        .baseUrl(garageUrl)
                        .defaultHeader("X-Internal-Token", garageSecret)
                        .build();
        location =
                builder.clone()
                        .baseUrl(locationUrl)
                        .defaultHeader("X-Internal-Token", locationSecret)
                        .build();
        ride =
                builder.clone()
                        .baseUrl(rideUrl)
                        .defaultHeader("X-Internal-Token", rideSecret)
                        .build();
    }

    private <T> T call(String target, Supplier<T> action) {
        try {
            return action.get();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404)
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, target + " resource not found");
            if (e.getStatusCode().value() == 409)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, target + " rejected the transition");
            if (e.getStatusCode().value() == 410)
                throw new ResponseStatusException(HttpStatus.GONE, "USER_DELETED");
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, target + " unavailable");
        } catch (RestClientException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, target + " unavailable");
        }
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

    public Vehicle optionalVehicle(String id) {
        try {
            return vehicle(id);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == 404) return null;
            throw e;
        }
    }

    public Station station(String id) {
        return call(
                "Location",
                () ->
                        location.get()
                                .uri("/internal/v1/locations/{id}", id)
                                .retrieve()
                                .body(Station.class));
    }

    public Station optionalStation(String id) {
        if (id == null) return null;
        try {
            return station(id);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == 404) return null;
            throw e;
        }
    }

    public List<Station> stations() {
        return call(
                "Location",
                () ->
                        Arrays.asList(
                                location.get()
                                        .uri("/internal/v1/locations")
                                        .retrieve()
                                        .body(Station[].class)));
    }

    public void move(String id, String locationId) {
        call(
                "Garage",
                () ->
                        garage.put()
                                .uri("/internal/v1/vehicles/{id}/location", id)
                                .body(Map.of("locationId", locationId))
                                .retrieve()
                                .toBodilessEntity());
    }

    public RideResult start(String bookingId) {
        return call(
                "Ride",
                () ->
                        ride.post()
                                .uri("/internal/v1/rides/{id}/start", bookingId)
                                .header("Idempotency-Key", bookingId + ":start")
                                .retrieve()
                                .body(RideResult.class));
    }

    public RideResult complete(String bookingId, CompleteBookingCommand command) {
        return call(
                "Ride",
                () ->
                        ride.post()
                                .uri("/internal/v1/rides/{id}/complete", bookingId)
                                .header("Idempotency-Key", bookingId + ":complete")
                                .body(command)
                                .retrieve()
                                .body(RideResult.class));
    }

    public RideResult ride(String bookingId) {
        return call(
                "Ride",
                () ->
                        ride.get()
                                .uri("/internal/v1/rides/by-booking/{id}", bookingId)
                                .retrieve()
                                .body(RideResult.class));
    }

    public Unlock unlock(String bookingId) {
        try {
            return call(
                    "Ride",
                    () ->
                            ride.get()
                                    .uri("/internal/v1/bookings/{id}/unlock", bookingId)
                                    .retrieve()
                                    .body(Unlock.class));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == 404) return null;
            throw e;
        }
    }

    public RideResult cancel(String bookingId) {
        return call(
                "Ride",
                () ->
                        ride.post()
                                .uri("/internal/v1/rides/{id}/cancel", bookingId)
                                .header("Idempotency-Key", bookingId + ":cancel")
                                .retrieve()
                                .body(RideResult.class));
    }
}
