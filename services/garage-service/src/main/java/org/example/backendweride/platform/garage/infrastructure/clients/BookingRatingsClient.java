package org.example.backendweride.platform.garage.infrastructure.clients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class BookingRatingsClient {
    private final RestClient client;

    public BookingRatingsClient(
            RestClient.Builder builder,
            @Value("${weride.internal.targets.booking.url}") String url,
            @Value("${weride.internal.targets.booking.secret}") String secret) {
        client = builder.baseUrl(url).defaultHeader("X-Internal-Token", secret).build();
    }

    public Map<String, Double> averageRatingsByVehicle() {
        try {
            var result =
                    client.get()
                            .uri("/internal/v1/vehicles/ratings")
                            .retrieve()
                            .body(new ParameterizedTypeReference<Map<String, Double>>() {});
            return result == null ? Map.of() : result;
        } catch (RestClientException e) {
            return Map.of();
        }
    }

    public Double averageRating(String id) {
        return averageRatingsByVehicle().get(id);
    }
}
