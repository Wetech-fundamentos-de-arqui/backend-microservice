package org.example.backendweride.platform.booking.interfaces.resources;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(
        description =
                "Everything the owner can see about one booking in a single response: the booking"
                    + " plus its vehicle, stations and latest unlock request.")
public record BookingDetailsResource(
        BookingResource booking,
        @Schema(description = "Null when the vehicle no longer exists", nullable = true)
                VehicleSummary vehicle,
        @Schema(nullable = true) StationSummary startLocation,
        @Schema(nullable = true) StationSummary endLocation,
        @Schema(
                        description =
                                "Latest unlock request; only present while the booking is reserved"
                                    + " or in progress",
                        nullable = true)
                UnlockSummary unlock) {
    public record VehicleSummary(
            String id,
            String brand,
            String model,
            String type,
            String image,
            String color,
            double pricePerMinute,
            @Schema(
                            description =
                                    "Battery right now, not at booking time; only present while the"
                                        + " booking is reserved or in progress",
                            nullable = true)
                    Integer currentBattery) {}

    public record StationSummary(String id, String name, String address) {}

    public record UnlockSummary(
            String id, String status, String accessCode, Instant codeExpiresAt) {}
}
