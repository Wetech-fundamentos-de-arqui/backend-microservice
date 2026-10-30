package org.example.backendweride.platform.trip.domain.valueobjects;

import jakarta.persistence.Embeddable;

@Embeddable
public record RouteCoordinates(double lat, double lng) {
    public RouteCoordinates {
        if (!Double.isFinite(lat)
                || !Double.isFinite(lng)
                || Math.abs(lat) > 90
                || Math.abs(lng) > 180)
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid route coordinates");
    }
}
