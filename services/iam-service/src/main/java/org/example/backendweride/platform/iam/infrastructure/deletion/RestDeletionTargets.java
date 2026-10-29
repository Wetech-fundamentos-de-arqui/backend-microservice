package org.example.backendweride.platform.iam.infrastructure.deletion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.backendweride.platform.iam.application.internal.outboundservices.deletion.DeletionTargets;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.stream.Collectors;

@Component
@EnableConfigurationProperties(InternalTargetsProperties.class)
public class RestDeletionTargets implements DeletionTargets {
    private static final Logger LOGGER = LoggerFactory.getLogger(RestDeletionTargets.class);
    private static final String BOOKING = "booking";

    private final Map<String, RestClient> clients;

    public RestDeletionTargets(RestClient.Builder builder, InternalTargetsProperties properties) {
        this.clients = properties.targets().entrySet().stream()
                .filter(entry -> entry.getValue().url() != null && !entry.getValue().url().isBlank())
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> builder.clone()
                        .baseUrl(entry.getValue().url())
                        .defaultHeader("X-Internal-Token", entry.getValue().secret() == null ? "" : entry.getValue().secret())
                        .build()));
    }

    @Override
    public boolean isConfigured(String target) {
        return clients.containsKey(target);
    }

    @Override
    public Lock lockDeletion(String userId) {
        try {
            clients.get(BOOKING).post().uri("/internal/v1/users/{userId}/deletion-lock", userId)
                    .retrieve().toBodilessEntity();
            return Lock.LOCKED;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) return Lock.ACTIVE_BOOKINGS;
            LOGGER.warn("booking deletion-lock answered {}", e.getStatusCode());
            return Lock.UNAVAILABLE;
        } catch (RestClientException e) {
            LOGGER.warn("booking deletion-lock failed: {}", e.getMessage());
            return Lock.UNAVAILABLE;
        }
    }

    @Override
    public void abortDeletion(String userId) {
        if (!isConfigured(BOOKING)) return;
        try {
            clients.get(BOOKING).delete().uri("/internal/v1/users/{userId}/deletion-lock", userId)
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            LOGGER.warn("booking deletion-lock release failed: {}", e.getMessage());
        }
    }

    @Override
    public boolean deletePersonalData(String target, String userId, String workflowId) {
        try {
            clients.get(target).delete().uri("/internal/v1/users/{userId}/personal-data/{workflowId}", userId, workflowId)
                    .retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            LOGGER.warn("{} personal-data deletion not confirmed: {}", target, e.getMessage());
            return false;
        }
    }
}
