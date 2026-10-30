package org.example.backendweride.platform.booking.interfaces.resources;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description =
                "Free destination point of a ride. address is the human readable text resolved by"
                    + " the client and is ignored when quoting.")
public record DestinationResource(Double lat, Double lng, String address) {}
