package org.example.backendweride.platform.booking.interfaces;

import io.swagger.v3.oas.annotations.tags.Tag;

import io.swagger.v3.oas.annotations.responses.ApiResponses;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

import io.swagger.v3.oas.annotations.Parameter;

import io.swagger.v3.oas.annotations.Operation;

import org.example.backendweride.platform.booking.domain.model.aggregates.Booking;
import org.example.backendweride.platform.booking.domain.model.commands.CancelBookingCommand;
import org.example.backendweride.platform.booking.domain.model.commands.ArchiveBookingCommand;
import org.example.backendweride.platform.booking.domain.model.commands.StartRideCommand;
import org.example.backendweride.platform.booking.domain.model.queries.GetActiveBookingsByVehicleIdQuery;
import org.example.backendweride.platform.booking.domain.model.queries.GetAllBookingsByUserIdQuery;
import org.example.backendweride.platform.booking.domain.model.queries.GetBookingByIdQuery;
import org.example.backendweride.platform.booking.domain.services.BookingCommandService;
import org.example.backendweride.platform.booking.domain.services.BookingQueryService;
import org.example.backendweride.platform.booking.interfaces.resources.BookingResource;
import org.example.backendweride.platform.booking.interfaces.resources.CompleteBookingResource;
import org.example.backendweride.platform.booking.interfaces.resources.CreateBookingResource;
import org.example.backendweride.platform.booking.interfaces.resources.VehicleAvailabilityResource;
import org.example.backendweride.platform.booking.interfaces.transform.BookingResourceFromEntityAssembler;
import org.example.backendweride.platform.booking.interfaces.transform.CompleteBookingCommandFromResourceAssembler;
import org.example.backendweride.platform.booking.interfaces.transform.CreateBookingCommandFromResourceAssembler;
import org.example.backendweride.platform.iam.infrastructure.auth.model.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Tag(name = "Bookings", description = "Reserve vehicles and manage the booking lifecycle: create, start, complete, cancel, list and archive bookings of the authenticated user.")
@RestController
@RequestMapping(value = "/api/v1/bookings")
public class BookingsController {

    private final BookingCommandService bookingCommandService;
    private final BookingQueryService bookingQueryService;
    private final org.example.backendweride.platform.garage.infrastructure.persistence.jpa.VehicleRepository vehicleRepository;
    private final org.example.backendweride.platform.location.infrastructure.persistence.jpa.LocationRepository locationRepository;
    private final org.example.backendweride.platform.unlock.infrastructure.UnlockRequestRepository unlockRequestRepository;

    public BookingsController(BookingCommandService bookingCommandService, BookingQueryService bookingQueryService,
                              org.example.backendweride.platform.garage.infrastructure.persistence.jpa.VehicleRepository vehicleRepository,
                              org.example.backendweride.platform.location.infrastructure.persistence.jpa.LocationRepository locationRepository,
                              org.example.backendweride.platform.unlock.infrastructure.UnlockRequestRepository unlockRequestRepository) {
        this.bookingCommandService = bookingCommandService;
        this.bookingQueryService = bookingQueryService;
        this.vehicleRepository = vehicleRepository;
        this.locationRepository = locationRepository;
        this.unlockRequestRepository = unlockRequestRepository;
    }

    // Los ids de vehículo y estación se guardan como texto; uno no numérico se trata como inexistente.
    private static Optional<Long> numericId(String id) {
        try { return Optional.of(Long.parseLong(id)); } catch (NumberFormatException | NullPointerException e) { return Optional.empty(); }
    }

    private org.example.backendweride.platform.booking.interfaces.resources.BookingDetailsResource.StationSummary station(String id) {
        return numericId(id).flatMap(locationRepository::findById)
                .map(l -> new org.example.backendweride.platform.booking.interfaces.resources.BookingDetailsResource.StationSummary(id, l.getName(), l.getAddress()))
                .orElse(null);
    }

    /** Finds a booking and checks it belongs to the caller; empty means 404. */
    private Optional<Booking> ownedBooking(String bookingId, Authentication authentication) {
        var booking = bookingQueryService.handle(new GetBookingByIdQuery(bookingId));
        booking.ifPresent(b -> CurrentUser.requireSelf(authentication, b.getUserId()));
        return booking;
    }

    // 1. POST: Crear Reserva
    @Operation(summary = "Create a booking", description = "Reserve a vehicle for the authenticated user between a start and an end location. The userId in the body must match the authenticated account.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Booking created successfully"),
            @ApiResponse(responseCode = "409", description = "Critical scooter battery below 15 percent or vehicle already reserved in that time range"),
            @ApiResponse(responseCode = "400", description = "Booking could not be created"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "The resource belongs to another user")
    })
    @PostMapping
    public ResponseEntity<BookingResource> createBooking(@RequestBody CreateBookingResource resource, Authentication authentication) {
        CurrentUser.requireSelf(authentication, resource.userId());
        var command = CreateBookingCommandFromResourceAssembler.toCommandFromResource(resource);
        var booking = bookingCommandService.handle(command);

        if (booking.isEmpty()) return ResponseEntity.badRequest().build();

        var bookingResource = BookingResourceFromEntityAssembler.toResourceFromEntity(booking.get());
        return new ResponseEntity<>(bookingResource, HttpStatus.CREATED);
    }

    // 1b. POST: Cotizar un destino sin guardarlo
    @Operation(summary = "Quote a destination", description = "Estimate distance, minutes and cost of riding from the booking's pickup station to a free point. Nothing is saved. changeFee is only charged when the ride already started.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Quote computed"),
            @ApiResponse(responseCode = "400", description = "Invalid coordinates"),
            @ApiResponse(responseCode = "404", description = "Booking not found"),
            @ApiResponse(responseCode = "422", description = "Destination farther than 60 km from the pickup station")
    })
    @PostMapping("/{bookingId}/destination/quote")
    public ResponseEntity<org.example.backendweride.platform.booking.interfaces.resources.DestinationQuoteResource> quoteDestination(
            @PathVariable String bookingId,
            @RequestBody org.example.backendweride.platform.booking.interfaces.resources.DestinationResource resource,
            Authentication authentication) {
        var owned = ownedBooking(bookingId, authentication);
        if (owned.isEmpty()) return ResponseEntity.notFound().build();
        double changeFee = "in_progress".equals(owned.get().getStatus())
                ? org.example.backendweride.platform.booking.domain.model.valueobjects.TripEstimate.DESTINATION_CHANGE_FEE : 0.0;
        return bookingCommandService.quoteDestination(bookingId, resource.lat(), resource.lng())
                .map(estimate -> ResponseEntity.ok(new org.example.backendweride.platform.booking.interfaces.resources.DestinationQuoteResource(
                        estimate.distanceKm(), estimate.durationMinutes(), estimate.pricePerMinute(), estimate.estimatedCost(),
                        0.0, changeFee, estimate.estimatedCost() + changeFee)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // 1c. PUT: Fijar o cambiar el destino
    @Operation(summary = "Set or change the destination", description = "Store the free destination point and its estimate. Free while the booking is reserved; once the ride is in progress every change adds a fixed fee that is charged on completion.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Destination stored"),
            @ApiResponse(responseCode = "400", description = "Invalid coordinates"),
            @ApiResponse(responseCode = "404", description = "Booking not found"),
            @ApiResponse(responseCode = "409", description = "Booking is completed or cancelled"),
            @ApiResponse(responseCode = "422", description = "Destination farther than 60 km from the pickup station")
    })
    @PutMapping("/{bookingId}/destination")
    public ResponseEntity<BookingResource> setDestination(
            @PathVariable String bookingId,
            @RequestBody org.example.backendweride.platform.booking.interfaces.resources.DestinationResource resource,
            Authentication authentication) {
        if (ownedBooking(bookingId, authentication).isEmpty()) return ResponseEntity.notFound().build();
        return bookingCommandService.handle(new org.example.backendweride.platform.booking.domain.model.commands.SetDestinationCommand(
                        bookingId, resource.lat(), resource.lng(), resource.address()))
                .map(b -> ResponseEntity.ok(BookingResourceFromEntityAssembler.toResourceFromEntity(b)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // 2. PUT: Iniciar Viaje
    @Operation(summary = "Start a ride", description = "Mark the booking as started so the ride begins. Requires a destination set beforehand (409 otherwise).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ride started successfully"),
            @ApiResponse(responseCode = "404", description = "Booking not found"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "The resource belongs to another user")
    })
    @PutMapping("/{bookingId}/start")
    public ResponseEntity<BookingResource> startRide(@Parameter(description = "Unique identifier of the booking") @PathVariable String bookingId, Authentication authentication) {
        if (ownedBooking(bookingId, authentication).isEmpty()) return ResponseEntity.notFound().build();
        var booking = bookingCommandService.handle(new StartRideCommand(bookingId));

        if (booking.isEmpty()) return ResponseEntity.notFound().build();

        var resource = BookingResourceFromEntityAssembler.toResourceFromEntity(booking.get());
        return ResponseEntity.ok(resource);
    }

    // 3. POST: Completar Viaje
    @Operation(summary = "Complete a ride", description = "Finish the booking and atomically persist cost, distance, duration, average speed, an optional rating and up to 5000 observed GPS coordinates. Missing routeCoordinates stores an empty route.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Booking completed successfully"),
            @ApiResponse(responseCode = "404", description = "Booking not found"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "The resource belongs to another user")
    })
    @PostMapping("/{bookingId}/complete")
    public ResponseEntity<BookingResource> completeBooking(@Parameter(description = "Unique identifier of the booking") @PathVariable String bookingId, @RequestBody CompleteBookingResource resource, Authentication authentication) {
        if (ownedBooking(bookingId, authentication).isEmpty()) return ResponseEntity.notFound().build();
        var command = CompleteBookingCommandFromResourceAssembler.toCommandFromResource(bookingId, resource);
        var booking = bookingCommandService.handle(command);

        if (booking.isEmpty()) return ResponseEntity.notFound().build();

        var bookingResource = BookingResourceFromEntityAssembler.toResourceWithRoute(booking.get());
        return ResponseEntity.ok(bookingResource);
    }

    // 4. GET: Listar reservas del usuario autenticado (filtro opcional por vehículo)
    @Operation(summary = "Rate a completed booking", description = "Owner only. Score 1–5, optional comment up to 1000 characters and fixed tags. Returns 409 if not completed or already rated; 400 for invalid input.")
    @PostMapping("/{bookingId}/rating")
    public ResponseEntity<BookingResource> rateBooking(@PathVariable String bookingId,
            @RequestBody org.example.backendweride.platform.booking.interfaces.resources.RateBookingResource resource,
            Authentication authentication) {
        return bookingCommandService.handle(new org.example.backendweride.platform.booking.domain.model.commands.RateBookingCommand(
                bookingId, String.valueOf(CurrentUser.id(authentication)), resource.score(), resource.comment(), resource.tags()))
                .map(b -> ResponseEntity.ok(BookingResourceFromEntityAssembler.toResourceFromEntity(b)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get my bookings", description = "List the bookings of the authenticated user, optionally filtered by vehicle.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bookings retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token")
    })
    @GetMapping
    public ResponseEntity<List<BookingResource>> getAllBookings(@Parameter(description = "Optional vehicle ID used to filter the bookings") @RequestParam(required = false) String vehicleId, Authentication authentication) {
        var userId = String.valueOf(CurrentUser.id(authentication));
        var resources = bookingQueryService.handle(new GetAllBookingsByUserIdQuery(userId)).stream()
                .filter(b -> vehicleId == null || vehicleId.equals(b.getVehicleId()))
                .map(BookingResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(resources);
    }

    @Operation(summary = "Get my trip history", description = "Page completed bookings owned by the authenticated account, including persisted metrics, final cost and recorded route. Old rides remain visible with an empty route.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "History page returned"),
            @ApiResponse(responseCode = "400", description = "Invalid page or size"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token")})
    @GetMapping("/history")
    public org.example.backendweride.platform.booking.interfaces.resources.BookingHistoryResource getHistory(
            @Parameter(description = "Zero based page", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size from 1 to 100", example = "10") @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        var history = bookingQueryService.handle(new org.example.backendweride.platform.booking.domain.model.queries.GetBookingHistoryByUserIdQuery(
                String.valueOf(CurrentUser.id(authentication)), page, size));
        return new org.example.backendweride.platform.booking.interfaces.resources.BookingHistoryResource(
                history.getContent().stream().map(BookingResourceFromEntityAssembler::toResourceFromEntity).toList(),
                history.getTotalElements(), history.getTotalPages(), history.getNumber(), history.getSize());
    }

    @Operation(summary = "Get a completed ride", description = "Return one completed booking owned by the authenticated account with its recorded route and ride metrics.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Completed ride returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Ride belongs to another account"),
            @ApiResponse(responseCode = "404", description = "Completed ride not found")})
    @GetMapping("/history/{bookingId}")
    public ResponseEntity<BookingResource> getHistoryDetail(
            @Parameter(description = "Completed booking UUID") @PathVariable String bookingId, Authentication authentication) {
        return ownedBooking(bookingId, authentication).filter(booking -> "completed".equals(booking.getStatus()))
                .map(booking -> ResponseEntity.ok(BookingResourceFromEntityAssembler.toResourceWithRoute(booking)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get a booking", description = "Return one booking owned by the authenticated user, without the recorded route (use the history detail for that).")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Booking returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Booking belongs to another account"),
            @ApiResponse(responseCode = "404", description = "Booking not found")})
    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResource> getBooking(@Parameter(description = "Unique identifier of the booking") @PathVariable String bookingId, Authentication authentication) {
        return ownedBooking(bookingId, authentication)
                .map(booking -> ResponseEntity.ok(BookingResourceFromEntityAssembler.toResourceFromEntity(booking)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get booking details", description = "Return one booking owned by the authenticated user together with its vehicle, start and end stations and, while the booking is reserved or in progress, the vehicle's current battery and the latest unlock request with its access code.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Booking details retrieved"),
            @ApiResponse(responseCode = "404", description = "Booking not found"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "The resource belongs to another user")
    })
    @GetMapping("/{bookingId}/details")
    public ResponseEntity<org.example.backendweride.platform.booking.interfaces.resources.BookingDetailsResource> getBookingDetails(
            @Parameter(description = "Unique identifier of the booking") @PathVariable String bookingId, Authentication authentication) {
        return ownedBooking(bookingId, authentication).map(booking -> {
            boolean active = Booking.ACTIVE_STATUSES.contains(booking.getStatus());
            var vehicle = numericId(booking.getVehicleId()).flatMap(vehicleRepository::findById)
                    .map(v -> new org.example.backendweride.platform.booking.interfaces.resources.BookingDetailsResource.VehicleSummary(
                            booking.getVehicleId(), v.getBrand(), v.getModel(), v.getType(), v.getImage(), v.getColor(),
                            v.getPricePerMinute(), active ? v.getBattery() : null))
                    .orElse(null);
            var unlock = !active ? null : unlockRequestRepository.findAllByUserIdOrderByRequestedAtDesc(booking.getUserId()).stream()
                    .filter(r -> bookingId.equals(r.getBookingId())).findFirst()
                    .map(r -> new org.example.backendweride.platform.booking.interfaces.resources.BookingDetailsResource.UnlockSummary(
                            r.getId(), r.getStatus(), r.getAccessCode(), r.getCodeExpiresAt()))
                    .orElse(null);
            return ResponseEntity.ok(new org.example.backendweride.platform.booking.interfaces.resources.BookingDetailsResource(
                    BookingResourceFromEntityAssembler.toResourceFromEntity(booking), vehicle,
                    station(booking.getStartLocationId()), station(booking.getEndLocationId()), unlock));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // 4b. GET: Disponibilidad de un vehículo considerando las reservas de todos los usuarios
    @Operation(summary = "Check vehicle availability", description = "Tell whether a vehicle is free between start and end (ISO-8601 instants) and list its busy slots. Slots are anonymous.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Availability computed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token")
    })
    @GetMapping("/availability")
    public ResponseEntity<VehicleAvailabilityResource> getAvailability(@RequestParam String vehicleId, @RequestParam Instant start, @RequestParam Instant end) {
        var from = Date.from(start);
        var to = Date.from(end);
        var busy = bookingQueryService.handle(new GetActiveBookingsByVehicleIdQuery(vehicleId)).stream()
                .filter(b -> b.busyUntil().after(new Date()))
                .sorted(Comparator.comparing(Booking::busyFrom))
                .toList();
        var slots = busy.stream().map(b -> new VehicleAvailabilityResource.BusySlot(b.busyFrom(), b.busyUntil())).toList();
        return ResponseEntity.ok(new VehicleAvailabilityResource(busy.stream().noneMatch(b -> b.overlaps(from, to)), slots));
    }

    // 5. PUT: Archivar reserva (sustituye al borrado: el historial se conserva)
    @Operation(summary = "Archive a booking", description = "Mark a completed or cancelled booking of the authenticated user as archived. The booking is kept and still returned by the list endpoints with archived=true.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Booking archived successfully"),
            @ApiResponse(responseCode = "404", description = "Booking not found"),
            @ApiResponse(responseCode = "409", description = "Booking is still reserved or in progress"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "The resource belongs to another user")
    })
    @PutMapping("/{bookingId}/archive")
    public ResponseEntity<BookingResource> archiveBooking(@Parameter(description = "Unique identifier of the booking") @PathVariable String bookingId, Authentication authentication) {
        if (ownedBooking(bookingId, authentication).isEmpty()) return ResponseEntity.notFound().build();
        return bookingCommandService.handle(new ArchiveBookingCommand(bookingId))
                .map(b -> ResponseEntity.ok(BookingResourceFromEntityAssembler.toResourceFromEntity(b)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // 6. PATCH: Cancelar reserva
    @Operation(summary = "Cancel a booking", description = "Cancel a booking of the authenticated user without deleting it.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Booking cancelled successfully"),
            @ApiResponse(responseCode = "404", description = "Booking not found"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "The resource belongs to another user")
    })
    @PatchMapping("/{bookingId}")
    public ResponseEntity<BookingResource> cancelBooking(@Parameter(description = "Unique identifier of the booking") @PathVariable String bookingId, Authentication authentication) {
        if (ownedBooking(bookingId, authentication).isEmpty()) return ResponseEntity.notFound().build();
        return bookingCommandService.handle(new CancelBookingCommand(bookingId))
                .map(b -> ResponseEntity.ok(BookingResourceFromEntityAssembler.toResourceFromEntity(b)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
