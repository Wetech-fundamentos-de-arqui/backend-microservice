package org.example.backendweride.platform.booking.interfaces.resources;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description =
                "Estimated cost of riding to a destination. discount stays at 0 until plans grant"
                    + " benefits. changeFee is charged only when the ride already started. total ="
                    + " estimatedCost - discount + changeFee.")
public record DestinationQuoteResource(
        double distanceKm,
        int durationMinutes,
        double pricePerMinute,
        double estimatedCost,
        double discount,
        double changeFee,
        double total) {}
