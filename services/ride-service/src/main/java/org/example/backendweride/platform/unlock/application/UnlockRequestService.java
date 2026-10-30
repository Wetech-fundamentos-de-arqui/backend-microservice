package org.example.backendweride.platform.unlock.application;

import org.example.backendweride.platform.ride.infrastructure.clients.RideDependencies;
import org.example.backendweride.platform.ride.infrastructure.outbox.OutboxService;
import org.example.backendweride.platform.ride.infrastructure.privacy.UserDataGuard;
import org.example.backendweride.platform.unlock.domain.model.*;
import org.example.backendweride.platform.unlock.infrastructure.UnlockRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
public class UnlockRequestService {
    private final UnlockRequestRepository requests;
    private final RideDependencies remote;
    private final UserDataGuard guard;
    private final OutboxService outbox;

    public UnlockRequestService(
            UnlockRequestRepository requests,
            RideDependencies remote,
            UserDataGuard guard,
            OutboxService outbox) {
        this.requests = requests;
        this.remote = remote;
        this.guard = guard;
        this.outbox = outbox;
    }

    public List<UnlockRequest> list(String userId, String bookingId) {
        return requests.findAllByUserIdOrderByRequestedAtDesc(userId).stream()
                .filter(r -> bookingId == null || bookingId.equals(r.getBookingId()))
                .toList();
    }

    public UnlockRequest owned(String id, String userId) {
        var r =
                requests.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!userId.equals(r.getUserId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return r;
    }

    private RideDependencies.Booking booking(String id, String userId) {
        var b = remote.booking(id);
        if (!userId.equals(b.userId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!Set.of("reserved", "in_progress").contains(b.status()))
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "La reserva ya terminó o fue cancelada");
        return b;
    }

    private void method(String method) {
        if (!Set.of("manual", "qr_code").contains(method == null ? "" : method))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Método de desbloqueo inválido");
    }

    @Transactional
    public UnlockRequest create(String userId, String bookingId, String vehicleId, String method) {
        guard.assertWritable(userId);
        method(method);
        var b = booking(bookingId, userId);
        if (!Objects.equals(vehicleId, b.vehicleId()))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Vehicle does not match booking");
        var r =
                requests.save(
                        new UnlockRequest(
                                userId,
                                vehicleId,
                                bookingId,
                                b.startDate() == null ? Instant.now() : b.startDate().toInstant(),
                                method));
        outbox.enqueue(
                "ride.unlock-requested.v1",
                userId,
                "Reserva pendiente",
                "Tu código de desbloqueo es " + r.getAccessCode() + ". Caduca en 15 minutos.",
                r.getId(),
                "unlock_request");
        return r;
    }

    @Transactional
    public UnlockRequest attempt(String id, String userId, AttemptUnlockCommand command) {
        guard.assertWritable(userId);
        var r =
                requests.lockById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!userId.equals(r.getUserId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        method(command.method());
        if (command.unlockCode() == null
                || command.unlockCode().isBlank()
                || command.unlockCode().length() > 100)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Ingresa el código de desbloqueo");
        var b = booking(r.getBookingId(), userId);
        if (b.startDate() != null && b.startDate().toInstant().isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Espera la hora programada");
        if (b.endDate() != null && b.endDate().toInstant().isBefore(Instant.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La reserva ha vencido");
        if ("unlocked".equals(r.getStatus())) return r;
        if (r.getAttempts() >= 5)
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Demasiados intentos; crea otra solicitud");
        if ("maintenance".equals(remote.vehicle(r.getVehicleId()).status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehículo en mantenimiento");
        r.attempt(command.method(), command.unlockCode(), Instant.now());
        return requests.save(r);
    }
}
