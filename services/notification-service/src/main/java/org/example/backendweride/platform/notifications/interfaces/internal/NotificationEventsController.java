package org.example.backendweride.platform.notifications.interfaces.internal;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.example.backendweride.platform.notifications.domain.model.aggregates.Notification;
import org.example.backendweride.platform.notifications.domain.model.commands.CreateNotificationCommand;
import org.example.backendweride.platform.notifications.infrastructure.persistence.jpa.NotificationRepository;
import org.example.backendweride.platform.notifications.infrastructure.privacy.UserDataGuard;
import org.example.backendweride.platform.notifications.interfaces.transform.NotificationResourceFromEntityAssembler;
import org.example.backendweride.platform.shared.security.CurrentUser;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@RestController
@RequestMapping("/internal/v1/notification-events")
public class NotificationEventsController {
    public record Event(
            String eventType,
            String userId,
            String title,
            String message,
            String type,
            String category,
            String priority,
            String relatedEntityId,
            String relatedEntityType,
            String icon,
            String color,
            Instant occurredAt) {}

    private final NotificationRepository notifications;
    private final UserDataGuard guard;
    private final ObjectMapper json;

    public NotificationEventsController(
            NotificationRepository notifications, UserDataGuard guard, ObjectMapper json) {
        this.notifications = notifications;
        this.guard = guard;
        this.json = json;
    }

    @PutMapping("/{sourceService}/{eventId}")
    @Transactional
    public ResponseEntity<?> receive(
            @PathVariable String sourceService,
            @PathVariable String eventId,
            @RequestBody Event e,
            Authentication auth)
            throws Exception {
        if (!java.util.Set.of("booking", "ride").contains(sourceService))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid source service");
        CurrentUser.requireService(auth, sourceService);
        if (eventId.isBlank()
                || eventId.length() > 128
                || e.userId() == null
                || e.eventType() == null
                || !e.eventType().endsWith(".v1")
                || e.occurredAt() == null
                || e.title() == null
                || e.message() == null
                || e.message().length() > 2000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid event");
        var state = guard.lock(e.userId());
        if (state.deleted()) return ResponseEntity.noContent().build();
        String hash =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(json.writeValueAsBytes(e)));
        var existing = notifications.findBySourceServiceAndEventId(sourceService, eventId);
        if (existing.isPresent()) {
            if (!hash.equals(existing.get().getEventHash()))
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Event id reused with a different payload");
            return ResponseEntity.ok(
                    NotificationResourceFromEntityAssembler.toResourceFromEntity(existing.get()));
        }
        var notification =
                new Notification(
                        new CreateNotificationCommand(
                                e.userId(),
                                e.title(),
                                e.message(),
                                e.type(),
                                e.category(),
                                e.priority(),
                                e.relatedEntityId(),
                                e.relatedEntityType(),
                                e.icon(),
                                e.color()));
        notification.event(sourceService, eventId, hash, e.occurredAt());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        NotificationResourceFromEntityAssembler.toResourceFromEntity(
                                notifications.saveAndFlush(notification)));
    }
}
