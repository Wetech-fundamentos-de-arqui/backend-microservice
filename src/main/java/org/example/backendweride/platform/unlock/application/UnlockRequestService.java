package org.example.backendweride.platform.unlock.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.example.backendweride.platform.booking.domain.model.aggregates.Booking;
import org.example.backendweride.platform.garage.infrastructure.persistence.jpa.VehicleRepository;
import org.example.backendweride.platform.notifications.domain.model.commands.CreateNotificationCommand;
import org.example.backendweride.platform.notifications.domain.services.NotificationCommandService;
import org.example.backendweride.platform.unlock.domain.model.AttemptUnlockCommand;
import org.example.backendweride.platform.unlock.domain.model.UnlockRequest;
import org.example.backendweride.platform.unlock.infrastructure.UnlockRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;

@Service
public class UnlockRequestService {
    private static final int MAX_ATTEMPTS = 5;
    private final UnlockRequestRepository requests;
    private final VehicleRepository vehicles;
    private final EntityManager entities;
    private final NotificationCommandService notifications;

    public UnlockRequestService(UnlockRequestRepository requests, VehicleRepository vehicles, EntityManager entities,
                                NotificationCommandService notifications) {
        this.requests = requests;
        this.vehicles = vehicles;
        this.entities = entities;
        this.notifications = notifications;
    }

    public List<UnlockRequest> list(String userId, String bookingId) {
        return requests.findAllByUserIdOrderByRequestedAtDesc(userId).stream()
                .filter(r -> bookingId == null || bookingId.equals(r.getBookingId())).toList();
    }

    public UnlockRequest owned(String id, String userId) {
        var request = requests.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!request.getUserId().equals(userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return request;
    }

    private Booking booking(String id, String userId) {
        var booking = entities.find(Booking.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (booking == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!booking.getUserId().equals(userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!Booking.ACTIVE_STATUSES.contains(booking.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La reserva ya terminó o fue cancelada");
        return booking;
    }

    private void validateMethod(String method) {
        if (!"manual".equals(method) && !"qr_code".equals(method))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Método de desbloqueo inválido");
    }

    @Transactional
    public UnlockRequest create(String userId, String bookingId, String vehicleId, String method) {
        validateMethod(method);
        var booking = booking(bookingId, userId);
        if (!booking.getVehicleId().equals(vehicleId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El vehículo no corresponde a la reserva");
        var request = requests.save(new UnlockRequest(userId, vehicleId, bookingId,
                booking.getStartDate() == null ? Instant.now() : booking.getStartDate().toInstant(), method));
        // El código también queda en la campana de notificaciones; relatedEntityId permite consultar su caducidad.
        notifications.handle(new CreateNotificationCommand(userId, "Reserva pendiente",
                "Tu código de desbloqueo es " + request.getAccessCode() + ". Caduca en "
                        + UnlockRequest.CODE_TTL.toMinutes() + " minutos.",
                "info", "booking", "high", request.getId(), "unlock_request", "lock_open", "#7145d8"));
        return request;
    }

    @Transactional
    public UnlockRequest attempt(String id, String userId, AttemptUnlockCommand command) {
        var request = entities.find(UnlockRequest.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (request == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!request.getUserId().equals(userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        validateMethod(command.method());
        if (command.unlockCode() == null || command.unlockCode().isBlank() || command.unlockCode().length() > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa el código de desbloqueo");
        var booking = booking(request.getBookingId(), userId);
        if (booking.getStartDate() != null && booking.getStartDate().toInstant().isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Espera la hora programada de tu reserva");
        if (booking.getEndDate() != null && booking.getEndDate().toInstant().isBefore(Instant.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La reserva ha vencido");
        if ("unlocked".equals(request.getStatus())) return request;
        if (request.getAttempts() >= MAX_ATTEMPTS)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Demasiados intentos fallidos; crea una nueva solicitud de desbloqueo");
        long vehicleId;
        try { vehicleId = Long.parseLong(request.getVehicleId()); }
        catch (NumberFormatException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador de vehículo inválido"); }
        var vehicle = vehicles.findById(vehicleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if ("maintenance".equals(vehicle.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El vehículo está en mantenimiento");
        request.attempt(command.method(), command.unlockCode(), Instant.now());
        // El desbloqueo ya no inicia el viaje: arranca cuando el usuario confirma el destino (PUT /bookings/{id}/start).
        return requests.save(request);
    }
}
