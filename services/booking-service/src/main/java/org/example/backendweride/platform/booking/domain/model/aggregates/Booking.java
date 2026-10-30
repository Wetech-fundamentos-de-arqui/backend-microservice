package org.example.backendweride.platform.booking.domain.model.aggregates;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;

import org.example.backendweride.platform.booking.domain.model.commands.CreateBookingCommand;
import org.example.backendweride.platform.booking.domain.model.valueobjects.Rating;
import org.example.backendweride.platform.booking.domain.model.valueobjects.TripRouteCoordinate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
public class Booking {

    @Id private String id;

    private String userId;
    private String vehicleId;
    private String startLocationId;
    private String endLocationId;

    private Date reservedAt;

    // **ARREGLADO:** Inicialización nula para quitar las advertencias de compilación
    // Estas fechas se llenarán con los datos del Frontend/Comando.
    private Date startDate = null;
    private Date endDate = null;
    private Date actualStartDate = null;
    private Date actualEndDate = null;

    // Campos inicializados para evitar advertencias y garantizar valor por defecto
    private String status;
    private Double totalCost = 0.0;
    private Double discount = 0.0;
    private Double finalCost = 0.0;
    private String paymentMethod = "card";
    private String paymentStatus = "pending";

    private Double distance = 0.0;
    private Integer duration = 0;
    private Double averageSpeed = 0.0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<TripRouteCoordinate> routeCoordinates = List.of();

    private String routeSource = "gps";

    // Destino libre marcado por el usuario. Columnas opcionales: las reservas anteriores las tienen
    // en null.
    private Double destinationLat;
    private Double destinationLng;
    private String destinationAddress;
    private Double estimatedDistance;
    private Integer estimatedDuration;
    private Double estimatedCost;
    private Double destinationChangeFees;

    // Fixed when the ride starts, so a later catalog change or a Garage outage cannot alter the bill.
    private Double pricePerMinute;

    public void lockPrice(double pricePerMinute) {
        this.pricePerMinute = pricePerMinute;
    }

    /** Whole minutes elapsed, at least one; a simulated ride also bills its estimated duration. */
    public int billableMinutes(Date end, boolean simulated) {
        int elapsed =
                (int) Math.ceil(Math.max(0, end.getTime() - actualStartDate.getTime()) / 60000.0);
        int minutes = Math.max(1, elapsed);
        return simulated && estimatedDuration != null
                ? Math.max(minutes, estimatedDuration)
                : minutes;
    }

    public boolean hasDestination() {
        return destinationLat != null && destinationLng != null;
    }

    public double destinationChangeFeesOrZero() {
        return destinationChangeFees == null ? 0.0 : destinationChangeFees;
    }

    /**
     * Free while the booking is only reserved; every change after the ride started adds a fixed
     * fee.
     */
    public void setDestination(
            double lat,
            double lng,
            String address,
            org.example.backendweride.platform.booking.domain.model.valueobjects.TripEstimate
                    estimate) {
        if ("in_progress".equals(status))
            this.destinationChangeFees =
                    destinationChangeFeesOrZero()
                            + org.example.backendweride.platform.booking.domain.model.valueobjects
                                    .TripEstimate.DESTINATION_CHANGE_FEE;
        else if (!"reserved".equals(status))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Booking destination can no longer change");
        this.destinationLat = lat;
        this.destinationLng = lng;
        this.destinationAddress =
                address == null || address.isBlank()
                        ? null
                        : address.strip().substring(0, Math.min(address.strip().length(), 255));
        this.estimatedDistance = estimate.distanceKm();
        this.estimatedDuration = estimate.durationMinutes();
        this.estimatedCost = estimate.estimatedCost();
    }

    public List<TripRouteCoordinate> getRouteCoordinates() {
        return routeCoordinates == null ? List.of() : List.copyOf(routeCoordinates);
    }

    @Embedded private Rating rating;

    // CONSTRUCTOR: Se usa al crear la reserva (CreateBookingCommand)
    public Booking(CreateBookingCommand command) {
        this.id = UUID.randomUUID().toString();
        this.userId = command.userId();
        this.vehicleId = command.vehicleId();
        this.startLocationId = command.startLocationId();
        this.endLocationId = command.endLocationId();
        this.reservedAt = new Date();
        this.status = "reserved";
        this.startDate = command.startDate();
        this.endDate = command.endDate();
        if (command.totalCost() != null) {
            this.totalCost = command.totalCost();
            this.finalCost = command.totalCost();
        }

        // El resto de campos nulos/cero se inicializan en la declaración del campo.
    }

    public static final List<String> ACTIVE_STATUSES =
            List.of("reserved", "starting", "in_progress", "paused", "completing", "cancelling");

    /** A vehicle in one of these states is physically in use, whatever the planned end says. */
    public static final List<String> RIDING_STATUSES =
            List.of("starting", "in_progress", "paused", "completing", "cancelling");

    public void anonymize(String anonymousOwner) {
        if (ACTIVE_STATUSES.contains(status) || "paused".equals(status))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "ACTIVE_BOOKINGS");
        userId = anonymousOwner;
        rating = null;
        routeCoordinates = List.of();
        destinationLat = null;
        destinationLng = null;
        destinationAddress = null;
        pendingCompletion = null;
    }

    /** Window during which this booking keeps its vehicle busy. */
    public Date busyFrom() {
        return startDate != null
                ? startDate
                : actualStartDate != null ? actualStartDate : reservedAt;
    }

    // ponytail: reservas inmediatas no tienen endDate; se asumen 2 h. Guardar la duración estimada
    // si hace falta más precisión.
    public Date busyUntil() {
        var planned = endDate != null ? endDate : new Date(busyFrom().getTime() + 2 * 3600_000L);
        if (!RIDING_STATUSES.contains(status)) return planned;
        var inUse = new Date(System.currentTimeMillis() + 60_000L);
        return inUse.after(planned) ? inUse : planned;
    }

    public boolean overlaps(Date from, Date to) {
        return busyFrom().before(to) && busyUntil().after(from);
    }

    // MÉTODO: Para iniciar el viaje (Resuelve advertencia de startRide)
    public void startRide() {
        if (!"reserved".equals(status) || (startDate != null && startDate.after(new Date())))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Booking cannot start yet");
        this.status = "in_progress";
        this.actualStartDate = new Date();
    }

    // Nullable: las reservas anteriores a esta columna la tienen en null (= no archivada).
    private Boolean archived;

    public boolean isArchived() {
        return Boolean.TRUE.equals(archived);
    }

    /** Hides a finished booking from the default list without losing its history. */
    public void archive() {
        if (!"completed".equals(status) && !"cancelled".equals(status))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Only completed or cancelled bookings can be archived");
        this.archived = true;
    }

    public void cancel() {
        if ("completed".equals(status) || "cancelled".equals(status))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Booking cannot be cancelled");
        this.status = "cancelled";
    }

    /**
     * "simulated" marks a route interpolated between stations because no valid GPS fix was
     * recorded.
     */
    public void markRouteSource(String source) {
        if (source == null || source.isBlank()) {
            this.routeSource = "gps";
            return;
        }
        if (!"gps".equals(source) && !"simulated".equals(source))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid route source");
        this.routeSource = source;
    }

    public void markChargeWaived() {
        this.paymentStatus = "waived";
    }

    public void rate(Rating rating) {
        if (!"completed".equals(status) || (this.rating != null && this.rating.getScore() != null))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Booking must be completed and not already rated");
        this.rating = rating;
    }

    // MÉTODO: Para finalizar la reserva (Resuelve advertencia de completeBooking)
    public void completeBooking(
            Double totalCost,
            Double discount,
            Double distance,
            Integer duration,
            Double averageSpeed,
            Rating rating) {
        completeBooking(totalCost, discount, distance, duration, averageSpeed, rating, List.of());
    }

    public void completeBooking(
            Double totalCost,
            Double discount,
            Double distance,
            Integer duration,
            Double averageSpeed,
            Rating rating,
            List<TripRouteCoordinate> routeCoordinates) {
        var route = routeCoordinates == null ? List.<TripRouteCoordinate>of() : routeCoordinates;
        if (route.size() > 5000 || route.stream().anyMatch(java.util.Objects::isNull))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Route must contain at most 5000 valid coordinates");
        if (!"in_progress".equals(status) && !"completing".equals(status))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Booking is not in progress");
        if (totalCost == null
                || discount == null
                || distance == null
                || duration == null
                || averageSpeed == null
                || !Double.isFinite(totalCost)
                || !Double.isFinite(discount)
                || !Double.isFinite(distance)
                || !Double.isFinite(averageSpeed)
                || totalCost < 0
                || discount < 0
                || discount > totalCost
                || distance < 0
                || duration < 0
                || averageSpeed < 0)
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid trip metrics or cost");
        this.status = "completed";
        this.paymentStatus = waiverReportId == null ? "paid" : "waived";

        this.totalCost = totalCost;
        this.discount = discount;
        this.finalCost = totalCost - discount + destinationChangeFeesOrZero();

        this.distance = distance;
        this.duration = duration;
        this.averageSpeed = averageSpeed;

        this.actualEndDate = new Date();
        this.endDate = this.actualEndDate;
        this.rating = rating;
        this.routeCoordinates = List.copyOf(route);
    }

    @Column(columnDefinition = "LONGTEXT")
    private String pendingCompletion;

    @Column(length = 64)
    private String completionHash;

    private String waiverReportId;
    private boolean vehicleRelocated;

    public boolean expired(java.time.Instant now, long timeoutSeconds) {
        return "reserved".equals(status)
                && (startDate == null ? reservedAt : startDate)
                        .toInstant()
                        .plusSeconds(timeoutSeconds)
                        .isBefore(now);
    }

    public void stageStart() {
        if ("starting".equals(status) || "in_progress".equals(status)) return;
        if (!"reserved".equals(status)
                || (startDate != null && startDate.after(new Date()))
                || !hasDestination())
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Booking cannot start yet or has no destination");
        status = "starting";
    }

    public void confirmStart(Date startedAt) {
        if ("starting".equals(status)) {
            status = "in_progress";
            actualStartDate = startedAt;
        }
    }

    public void stageCompletion(String payload, String hash) {
        if ("completed".equals(status) || "completing".equals(status)) {
            if (!java.util.Objects.equals(completionHash, hash))
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "Completion payload differs from the original request");
            return;
        }
        if (!"in_progress".equals(status))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Booking is not in progress");
        status = "completing";
        pendingCompletion = payload;
        completionHash = hash;
    }

    public void confirmEndDate(Date endedAt) {
        actualEndDate = endedAt;
        endDate = endedAt;
    }

    public void markRelocated() {
        vehicleRelocated = true;
    }

    public void waiveFor(String reportId) {
        waiverReportId = reportId;
    }

    public void stageCancel() {
        if ("completed".equals(status) || "completing".equals(status))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Ride is completing or completed");
        if (!"cancelled".equals(status)) status = "cancelling";
    }
}
