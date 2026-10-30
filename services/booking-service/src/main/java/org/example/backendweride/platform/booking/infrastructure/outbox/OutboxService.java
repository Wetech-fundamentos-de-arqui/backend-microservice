package org.example.backendweride.platform.booking.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
public class OutboxService {
    private final OutboxRepository events;
    private final ObjectMapper json;
    private final RestClient notification;
    private final TransactionTemplate tx;

    public OutboxService(
            OutboxRepository events,
            ObjectMapper json,
            RestClient.Builder builder,
            PlatformTransactionManager manager,
            @Value("${weride.internal.targets.notification.url}") String url,
            @Value("${weride.internal.targets.notification.secret}") String secret) {
        this.events = events;
        this.json = json;
        this.tx = new TransactionTemplate(manager);
        notification = builder.baseUrl(url).defaultHeader("X-Internal-Token", secret).build();
    }

    @Transactional
    public String enqueue(
            String eventType,
            String userId,
            String title,
            String message,
            String relatedId,
            String relatedType) {
        var body = new LinkedHashMap<String, Object>();
        body.put("eventType", eventType);
        body.put("userId", userId);
        body.put("title", title);
        body.put("message", message);
        body.put("type", "info");
        body.put("category", relatedType);
        body.put("priority", "normal");
        body.put("relatedEntityId", relatedId);
        body.put("relatedEntityType", relatedType);
        body.put("icon", "notifications");
        body.put("color", "#7145d8");
        body.put("occurredAt", Instant.now().toString());
        try {
            return events.save(new OutboxEvent(userId, eventType, json.writeValueAsString(body)))
                    .getEventId();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize notification event", e);
        }
    }

    @Scheduled(fixedDelayString = "${weride.outbox.interval-ms:2000}", initialDelay = 2000)
    public void publish() {
        for (var event :
                events.findTop50ByStateAndNextAttemptAtLessThanEqualOrderByOccurredAtAsc(
                        "PENDING", Instant.now())) {
            tx.executeWithoutResult(
                    status ->
                            events.lock(event.getEventId())
                                    .ifPresent(
                                            current -> {
                                                if (!"PENDING".equals(current.getState())
                                                        || current.getNextAttemptAt()
                                                                .isAfter(Instant.now())) return;
                                                try {
                                                    notification
                                                            .put()
                                                            .uri(
                                                                    "/internal/v1/notification-events/booking/{id}",
                                                                    current.getEventId())
                                                            .contentType(
                                                                    org.springframework.http
                                                                            .MediaType
                                                                            .APPLICATION_JSON)
                                                            .body(current.getPayload())
                                                            .retrieve()
                                                            .toBodilessEntity();
                                                    current.delivered();
                                                } catch (RestClientException e) {
                                                    current.failed(
                                                            e
                                                                            instanceof
                                                                            RestClientResponseException
                                                                                    response
                                                                    ? "HTTP "
                                                                            + response.getStatusCode()
                                                                                    .value()
                                                                    : e.getClass().getSimpleName());
                                                }
                                                events.save(current);
                                            }));
        }
    }

    @Transactional
    public void replay(String id) {
        var event =
                events.lock(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!"FAILED".equals(event.getState()))
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only FAILED events can be replayed");
        event.replay();
        events.save(event);
    }
}
