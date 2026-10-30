package org.example.backendweride.platform.notifications.application.commandservices;

import org.example.backendweride.platform.notifications.domain.model.aggregates.Notification;
import org.example.backendweride.platform.notifications.domain.model.commands.CreateNotificationCommand;
import org.example.backendweride.platform.notifications.domain.model.commands.MarkNotificationAsReadCommand;
import org.example.backendweride.platform.notifications.domain.services.NotificationCommandService;
import org.example.backendweride.platform.notifications.infrastructure.persistence.jpa.NotificationRepository;
import org.springframework.stereotype.Service;

@Service
@org.springframework.transaction.annotation.Transactional
public class NotificationCommandServiceImpl implements NotificationCommandService {

    private final NotificationRepository notificationRepository;
    private final org.example.backendweride.platform.notifications.infrastructure.privacy
                    .UserDataGuard
            guard;

    public NotificationCommandServiceImpl(
            NotificationRepository notificationRepository,
            org.example.backendweride.platform.notifications.infrastructure.privacy.UserDataGuard
                    guard) {
        this.guard = guard;
        this.notificationRepository = notificationRepository;
    }

    @Override
    public Long handle(CreateNotificationCommand command) {
        guard.assertWritable(command.userId());
        var notification = new Notification(command);
        notificationRepository.save(notification);
        return notification.getId();
    }

    // Nueva implementación para marcar como leída
    @Override
    public void handle(MarkNotificationAsReadCommand command) {
        notificationRepository
                .findByPublicId(command.notificationId())
                .map(
                        notification -> {
                            guard.assertWritable(notification.getUserId());
                            notification.markAsRead();
                            notificationRepository.save(notification);
                            return notification;
                        })
                .orElseThrow(
                        () ->
                                new RuntimeException(
                                        "Notification not found with ID: "
                                                + command.notificationId()));
    }
}
