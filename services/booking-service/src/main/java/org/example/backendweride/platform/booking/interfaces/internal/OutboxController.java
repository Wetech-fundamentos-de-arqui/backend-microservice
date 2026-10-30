package org.example.backendweride.platform.booking.interfaces.internal;

import org.example.backendweride.platform.booking.infrastructure.outbox.*;
import org.example.backendweride.platform.shared.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/outbox")
public class OutboxController {
    private final OutboxRepository events;
    private final OutboxService outbox;

    public OutboxController(OutboxRepository events, OutboxService outbox) {
        this.events = events;
        this.outbox = outbox;
    }

    public record FailedEvent(
            String eventId,
            String eventType,
            int attempts,
            java.time.Instant nextAttemptAt,
            String lastError) {}

    @GetMapping("/failed")
    public List<FailedEvent> failed(Authentication auth) {
        CurrentUser.requireService(auth, "ops");
        return events.findTop100ByStateOrderByOccurredAtAsc("FAILED").stream()
                .map(
                        e ->
                                new FailedEvent(
                                        e.getEventId(),
                                        e.getEventType(),
                                        e.getAttempts(),
                                        e.getNextAttemptAt(),
                                        e.getLastError()))
                .toList();
    }

    @PostMapping("/{eventId}/replay")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void replay(@PathVariable String eventId, Authentication auth) {
        CurrentUser.requireService(auth, "ops");
        outbox.replay(eventId);
    }
}
