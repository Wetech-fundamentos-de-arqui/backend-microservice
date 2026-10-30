package org.example.backendweride.platform.booking.interfaces.resources;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.*;

@Schema(
        description =
                "Booking form values to save for later. Saving does not reserve a vehicle or check"
                    + " its availability.")
public record CreateBookingDraftResource(
        @Schema(
                        description = "Optional account ID; must match the authenticated user",
                        example = "1")
                String userId,
        @Schema(description = "Opaque existing vehicle ID", example = "7")
                @NotBlank
                @Size(max = 255)
                String vehicleId,
        @Schema(description = "Valid calendar date in YYYY-MM-DD format", example = "2026-10-02")
                @NotBlank
                @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}")
                String selectedDate,
        @Schema(description = "Valid local time in HH:mm format", example = "14:30")
                @NotBlank
                @Pattern(regexp = "[0-9]{2}:[0-9]{2}")
                String unlockTime,
        @Schema(
                        description = "Requested duration in hours, from 0.25 to 24",
                        minimum = "0.25",
                        maximum = "24",
                        example = "1")
                @DecimalMin("0.25")
                @DecimalMax("24")
                double duration,
        @Schema(description = "Saved SMS reminder preference; does not send an SMS")
                boolean smsReminder,
        @Schema(description = "Saved email confirmation preference; does not send an email")
                boolean emailConfirmation,
        @Schema(
                        description =
                                "Saved push notification preference; does not send a push"
                                    + " notification")
                boolean pushNotification) {}
