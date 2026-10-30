package org.example.backendweride.platform.booking.application.commandservices;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;

import org.example.backendweride.platform.booking.domain.model.aggregates.Booking;
import org.example.backendweride.platform.booking.domain.model.commands.*;
import org.example.backendweride.platform.booking.domain.model.valueobjects.*;
import org.example.backendweride.platform.booking.domain.services.BookingCommandService;
import org.example.backendweride.platform.booking.infrastructure.clients.BookingDependencies;
import org.example.backendweride.platform.booking.infrastructure.outbox.OutboxService;
import org.example.backendweride.platform.booking.infrastructure.persistence.jpa.BookingRepository;
import org.example.backendweride.platform.booking.infrastructure.privacy.UserDataGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class BookingCommandServiceImpl implements BookingCommandService {
    private final BookingRepository bookings;
    private final BookingDependencies remote;
    private final UserDataGuard guard;
    private final OutboxService outbox;
    private final EntityManager entities;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final long expirySeconds;

    public BookingCommandServiceImpl(
            BookingRepository bookings,
            BookingDependencies remote,
            UserDataGuard guard,
            OutboxService outbox,
            EntityManager entities,
            PlatformTransactionManager manager,
            ObjectMapper json,
            @Value("${weride.booking.expiry-seconds:900}") long expirySeconds) {
        this.bookings = bookings;
        this.remote = remote;
        this.guard = guard;
        this.outbox = outbox;
        this.entities = entities;
        this.tx = new TransactionTemplate(manager);
        this.json = json;
        this.expirySeconds = expirySeconds;
    }

    private void schedule(String vehicleId) {
        entities.createNativeQuery(
                        "INSERT INTO vehicle_schedules (vehicle_id) VALUES (:id) ON DUPLICATE KEY"
                            + " UPDATE vehicle_id=vehicle_id")
                .setParameter("id", vehicleId)
                .executeUpdate();
        entities.createNativeQuery(
                        "SELECT vehicle_id FROM vehicle_schedules WHERE vehicle_id=:id FOR UPDATE")
                .setParameter("id", vehicleId)
                .getSingleResult();
    }

    private Optional<Booking> locked(String id) {
        var rows =
                entities.createNativeQuery("SELECT user_id,vehicle_id FROM bookings WHERE id=:id")
                        .setParameter("id", id)
                        .getResultList();
        if (rows.isEmpty()) return Optional.empty();
        Object[] owner = (Object[]) rows.getFirst();
        guard.assertWritable((String) owner[0]);
        schedule((String) owner[1]);
        return bookings.findLockedById(id);
    }

    private String serialize(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid request", e);
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            value.getBytes(
                                                    java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private CompleteBookingCommand completion(Booking b) {
        try {
            return json.readValue(b.getPendingCompletion(), CompleteBookingCommand.class);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid persisted completion", e);
        }
    }

    public Optional<Booking> handle(CreateBookingCommand command) {
        return handle(command, null);
    }

    public Optional<Booking> handle(CreateBookingCommand command, String key) {
        return tx.execute(
                status -> {
                    guard.assertWritable(command.userId());
                    String requestHash = hash(serialize(command));
                    if (key != null) {
                        if (key.isBlank() || key.length() > 128)
                            throw new ResponseStatusException(
                                    HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key");
                        var old =
                                entities.createNativeQuery(
                                                "SELECT payload_hash,booking_id FROM"
                                                    + " booking_requests WHERE user_id=:user AND"
                                                    + " request_key=:key")
                                        .setParameter("user", command.userId())
                                        .setParameter("key", key)
                                        .getResultList();
                        if (!old.isEmpty()) {
                            Object[] row = (Object[]) old.getFirst();
                            if (!requestHash.equals(row[0]))
                                throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Idempotency-Key reused with a different payload");
                            return bookings.findById((String) row[1]);
                        }
                    }
                    if (command.vehicleId() == null || command.startLocationId() == null)
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Vehicle and pickup station are required");
                    schedule(command.vehicleId());
                    var vehicle = remote.vehicle(command.vehicleId());
                    remote.station(command.startLocationId());
                    if (command.endLocationId() != null) remote.station(command.endLocationId());
                    if ("maintenance".equals(vehicle.status()))
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT, "Vehicle is in maintenance");
                    if ("electric_scooter".equals(vehicle.type())
                            && vehicle.battery() != null
                            && vehicle.battery() < 15)
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Batería crítica");
                    var booking = new Booking(command);
                    var from = booking.busyFrom();
                    var to = booking.busyUntil();
                    if (!from.before(to))
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "End must be after start");
                    for (var active :
                            bookings.findActiveLocked(
                                    command.vehicleId(), Booking.ACTIVE_STATUSES)) {
                        if (active.expired(Instant.now(), expirySeconds)) {
                            active.cancel();
                            bookings.save(active);
                        } else if (active.overlaps(from, to))
                            throw new ResponseStatusException(
                                    HttpStatus.CONFLICT,
                                    "Vehicle is not available in that time range");
                    }
                    bookings.saveAndFlush(booking);
                    if (key != null)
                        entities.createNativeQuery(
                                        "INSERT INTO"
                                            + " booking_requests(user_id,request_key,payload_hash,booking_id)"
                                            + " VALUES(:user,:key,:hash,:booking)")
                                .setParameter("user", command.userId())
                                .setParameter("key", key)
                                .setParameter("hash", requestHash)
                                .setParameter("booking", booking.getId())
                                .executeUpdate();
                    outbox.enqueue(
                            "booking.created.v1",
                            command.userId(),
                            "Reserva confirmada",
                            "Tu reserva fue confirmada.",
                            booking.getId(),
                            "booking");
                    return Optional.of(booking);
                });
    }

    private TripEstimate estimate(Booking b, Double lat, Double lng) {
        if (lat == null
                || lng == null
                || !Double.isFinite(lat)
                || !Double.isFinite(lng)
                || Math.abs(lat) > 90
                || Math.abs(lng) > 180)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destino inválido");
        var station = remote.station(b.getStartLocationId());
        if (station.coordinates() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid pickup station");
        var origin = station.coordinates();
        if (TripEstimate.straightKm(origin.lat(), origin.lng(), lat, lng) > 60)
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "Destino fuera de cobertura");
        var vehicle = remote.vehicle(b.getVehicleId());
        return TripEstimate.between(
                origin.lat(), origin.lng(), lat, lng, vehicle.type(), vehicle.pricePerMinute());
    }

    public Optional<TripEstimate> quoteDestination(String id, Double lat, Double lng) {
        return bookings.findById(id).map(b -> estimate(b, lat, lng));
    }

    public Optional<Booking> handle(SetDestinationCommand c) {
        return tx.execute(
                status ->
                        locked(c.bookingId())
                                .map(
                                        b -> {
                                            b.setDestination(
                                                    c.lat(),
                                                    c.lng(),
                                                    c.address(),
                                                    estimate(b, c.lat(), c.lng()));
                                            return bookings.save(b);
                                        }));
    }

    public Optional<Booking> handle(StartRideCommand c) {
        var staged =
                tx.execute(
                        status ->
                                locked(c.bookingId())
                                        .map(
                                                b -> {
                                                    if (b.expired(Instant.now(), expirySeconds))
                                                        throw new ResponseStatusException(
                                                                HttpStatus.CONFLICT,
                                                                "Booking expired");
                                                    var v = remote.vehicle(b.getVehicleId());
                                                    if ("maintenance".equals(v.status()))
                                                        throw new ResponseStatusException(
                                                                HttpStatus.CONFLICT,
                                                                "Vehicle is in maintenance");
                                                    if ("reserved".equals(b.getStatus())) {
                                                        // The vehicle lock taken by locked() makes
                                                        // this check safe against concurrent starts.
                                                        if (bookings
                                                                .findActiveLocked(
                                                                        b.getVehicleId(),
                                                                        Booking.RIDING_STATUSES)
                                                                .stream()
                                                                .anyMatch(
                                                                        other ->
                                                                                !other.getId()
                                                                                        .equals(
                                                                                                b
                                                                                                        .getId())))
                                                            throw new ResponseStatusException(
                                                                    HttpStatus.CONFLICT,
                                                                    "Vehicle is in use");
                                                        b.lockPrice(v.pricePerMinute());
                                                    }
                                                    b.stageStart();
                                                    return bookings.save(b);
                                                }));
        if (staged.isEmpty() || "in_progress".equals(staged.get().getStatus())) return staged;
        var result = remote.start(c.bookingId());
        return confirmStart(c.bookingId(), result);
    }

    private Optional<Booking> confirmStart(String id, BookingDependencies.RideResult result) {
        return tx.execute(
                status ->
                        locked(id)
                                .map(
                                        b -> {
                                            if ("starting".equals(b.getStatus())) {
                                                b.confirmStart(result.startDate());
                                                outbox.enqueue(
                                                        "booking.started.v1",
                                                        b.getUserId(),
                                                        "Viaje iniciado",
                                                        "Tu viaje está en curso.",
                                                        b.getId(),
                                                        "booking");
                                            }
                                            return bookings.save(b);
                                        }));
    }

    public Optional<Booking> handle(CompleteBookingCommand c) {
        validateCompletion(c);
        var staged =
                tx.execute(
                        status ->
                                locked(c.bookingId())
                                        .map(
                                                b -> {
                                                    var payload =
                                                            "in_progress".equals(b.getStatus())
                                                                    ? priced(b, c)
                                                                    : c;
                                                    b.stageCompletion(
                                                            serialize(payload), hash(serialize(c)));
                                                    return bookings.save(b);
                                                }));
        if (staged.isEmpty() || "completed".equals(staged.get().getStatus())) return staged;
        return finish(staged.get());
    }

    /** Replaces the client's figures: the bill comes only from server-side time and price. */
    private CompleteBookingCommand priced(Booking b, CompleteBookingCommand c) {
        double price =
                b.getPricePerMinute() != null
                        ? b.getPricePerMinute()
                        : remote.vehicle(b.getVehicleId()).pricePerMinute();
        int minutes = b.billableMinutes(new java.util.Date(), "simulated".equals(c.routeSource()));
        return new CompleteBookingCommand(
                c.bookingId(),
                Math.round(minutes * price * 100) / 100.0,
                0.0,
                c.distance(),
                minutes,
                c.distance() / (minutes / 60.0),
                c.ratingScore(),
                c.ratingComment(),
                c.routeCoordinates(),
                c.routeSource());
    }

    private void validateCompletion(CompleteBookingCommand c) {
        if (c.distance() == null
                || !Double.isFinite(c.distance())
                || c.distance() < 0
                || (c.routeCoordinates() != null
                        && (c.routeCoordinates().size() > 5000
                                || c.routeCoordinates().stream().anyMatch(Objects::isNull))))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid trip metrics or route");
        if (c.ratingScore() != null) new Rating(c.ratingScore(), c.ratingComment());
        if (c.routeSource() != null && !Set.of("gps", "simulated").contains(c.routeSource()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid route source");
    }

    private Optional<Booking> finish(Booking staged) {
        var command = completion(staged);
        var result = remote.complete(staged.getId(), command);
        var confirmed =
                tx.execute(
                        status ->
                                locked(staged.getId())
                                        .map(
                                                b -> {
                                                    if ("completing".equals(b.getStatus())) {
                                                        var rating =
                                                                command.ratingScore() == null
                                                                        ? null
                                                                        : new Rating(
                                                                                command
                                                                                        .ratingScore(),
                                                                                command
                                                                                        .ratingComment());
                                                        b.completeBooking(
                                                                result.totalCost(),
                                                                command.discount(),
                                                                result.distance(),
                                                                result.duration(),
                                                                result.averageSpeed(),
                                                                rating,
                                                                result.routeCoordinates());
                                                        b.markRouteSource(result.routeSource());
                                                        b.confirmEndDate(result.endDate());
                                                        outbox.enqueue(
                                                                "booking.completed.v1",
                                                                b.getUserId(),
                                                                "Viaje finalizado",
                                                                "Tu viaje finalizó correctamente.",
                                                                b.getId(),
                                                                "booking");
                                                    }
                                                    return bookings.save(b);
                                                }));
        confirmed.ifPresent(
                b -> {
                    try {
                        relocate(b);
                    } catch (RuntimeException ignored) {
                        /* durable flag keeps relocation pending */
                    }
                });
        return confirmed;
    }

    private void relocate(Booking booking) {
        if (booking.isVehicleRelocated()) return;
        if (booking.hasDestination()) {
            var nearest =
                    remote.stations().stream()
                            .filter(s -> s.coordinates() != null)
                            .min(
                                    Comparator.comparingDouble(
                                            s ->
                                                    TripEstimate.straightKm(
                                                            s.coordinates().lat(),
                                                            s.coordinates().lng(),
                                                            booking.getDestinationLat(),
                                                            booking.getDestinationLng())));
            if (nearest.isPresent()) remote.move(booking.getVehicleId(), nearest.get().id());
        }
        tx.executeWithoutResult(
                status ->
                        bookings.findLockedById(booking.getId())
                                .ifPresent(
                                        b -> {
                                            b.markRelocated();
                                            bookings.save(b);
                                        }));
    }

    public Optional<Booking> handle(CancelBookingCommand c) {
        var staged =
                tx.execute(
                        status ->
                                locked(c.bookingId())
                                        .map(
                                                b -> {
                                                    if ("cancelled".equals(b.getStatus())) return b;
                                                    if ("reserved".equals(b.getStatus())) {
                                                        b.cancel();
                                                        outbox.enqueue(
                                                                "booking.cancelled.v1",
                                                                b.getUserId(),
                                                                "Reserva cancelada",
                                                                "Tu reserva fue cancelada.",
                                                                b.getId(),
                                                                "booking");
                                                    } else b.stageCancel();
                                                    return bookings.save(b);
                                                }));
        if (staged.isEmpty() || "cancelled".equals(staged.get().getStatus())) return staged;
        return finishCancellation(staged.get());
    }

    private Optional<Booking> finishCancellation(Booking staged) {
        var result = remote.cancel(staged.getId());
        return tx.execute(
                status ->
                        locked(staged.getId())
                                .map(
                                        b -> {
                                            if ("cancelling".equals(b.getStatus())) {
                                                b.cancel();
                                                b.confirmEndDate(result.endDate());
                                                outbox.enqueue(
                                                        "booking.cancelled.v1",
                                                        b.getUserId(),
                                                        "Reserva cancelada",
                                                        "Tu reserva fue cancelada.",
                                                        b.getId(),
                                                        "booking");
                                            }
                                            return bookings.save(b);
                                        }));
    }

    public Optional<Booking> handle(ArchiveBookingCommand c) {
        return tx.execute(
                status ->
                        locked(c.bookingId())
                                .map(
                                        b -> {
                                            b.archive();
                                            return bookings.save(b);
                                        }));
    }

    public Optional<Booking> handle(RateBookingCommand c) {
        return tx.execute(
                status ->
                        locked(c.bookingId())
                                .map(
                                        b -> {
                                            if (!b.getUserId().equals(c.userId()))
                                                throw new ResponseStatusException(
                                                        HttpStatus.FORBIDDEN);
                                            b.rate(new Rating(c.score(), c.comment(), c.tags()));
                                            return bookings.save(b);
                                        }));
    }

    public void prepareDeletion(String userId) {
        tx.executeWithoutResult(
                status -> {
                    var state = guard.lock(userId);
                    if (state.deleted()) return;
                    var own = bookings.findAllByUserId(userId);
                    for (var b : own)
                        if (b.expired(Instant.now(), expirySeconds)) {
                            b.cancel();
                            bookings.save(b);
                        }
                    if (own.stream().anyMatch(b -> Booking.ACTIVE_STATUSES.contains(b.getStatus())))
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "ACTIVE_BOOKINGS");
                    guard.block(userId);
                });
    }

    public boolean reportFault(
            String id, String userId, String vehicleId, String reportId, Instant reportedAt) {
        var b =
                bookings.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!Objects.equals(b.getUserId(), userId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!Objects.equals(b.getVehicleId(), vehicleId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vehicle mismatch");
        if (reportId.equals(b.getWaiverReportId())) {
            if ("completing".equals(b.getStatus())) finish(b);
            return true;
        }
        if ("completed".equals(b.getStatus())) return false;
        if (!"in_progress".equals(b.getStatus()) || b.getActualStartDate() == null) return false;
        long elapsed = reportedAt.toEpochMilli() - b.getActualStartDate().getTime();
        if (elapsed < 0 || elapsed > 60000) return false;
        var command =
                new CompleteBookingCommand(
                        id,
                        0.0,
                        0.0,
                        0.0,
                        (int) (elapsed / 60000),
                        0.0,
                        null,
                        null,
                        List.of(),
                        "gps");
        var staged =
                tx.execute(
                        status ->
                                locked(id)
                                        .map(
                                                current -> {
                                                    if (!"in_progress".equals(current.getStatus()))
                                                        throw new ResponseStatusException(
                                                                HttpStatus.CONFLICT,
                                                                "Ride transition in progress");
                                                    current.waiveFor(reportId);
                                                    current.stageCompletion(
                                                            serialize(command),
                                                            hash(serialize(command)));
                                                    return bookings.save(current);
                                                })
                                        .orElseThrow());
        finish(staged);
        return true;
    }

    @Scheduled(fixedDelayString = "${weride.booking.reconcile-ms:5000}", initialDelay = 5000)
    public void reconcile() {
        for (var b : bookings.findByStatus("reserved"))
            if (b.expired(Instant.now(), expirySeconds))
                try {
                    tx.executeWithoutResult(
                            status ->
                                    locked(b.getId())
                                            .ifPresent(
                                                    current -> {
                                                        if (current.expired(
                                                                Instant.now(), expirySeconds)) {
                                                            current.cancel();
                                                            bookings.save(current);
                                                        }
                                                    }));
                } catch (RuntimeException ignored) {
                }
        for (var b : bookings.findByStatus("starting"))
            try {
                confirmStart(b.getId(), remote.start(b.getId()));
            } catch (RuntimeException ignored) {
            }
        for (var b : bookings.findByStatus("cancelling"))
            try {
                finishCancellation(b);
            } catch (RuntimeException ignored) {
            }
        for (var b : bookings.findByStatus("completing"))
            try {
                finish(b);
            } catch (RuntimeException ignored) {
            }
        for (var b : bookings.findByStatusAndVehicleRelocatedFalse("completed"))
            try {
                relocate(b);
            } catch (RuntimeException ignored) {
            }
    }
}
