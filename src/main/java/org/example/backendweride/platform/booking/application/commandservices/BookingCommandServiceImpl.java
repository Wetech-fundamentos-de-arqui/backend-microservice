package org.example.backendweride.platform.booking.application.commandservices;

import org.example.backendweride.platform.booking.domain.model.aggregates.Booking;
import org.example.backendweride.platform.booking.domain.model.commands.CompleteBookingCommand; // <--- Importante
import org.example.backendweride.platform.booking.domain.model.commands.CreateBookingCommand;
import org.example.backendweride.platform.booking.domain.model.commands.StartRideCommand;
import org.example.backendweride.platform.booking.domain.model.commands.CancelBookingCommand;
import org.example.backendweride.platform.booking.domain.model.commands.DeleteBookingCommand;
import org.example.backendweride.platform.booking.domain.model.commands.SetDestinationCommand;
import org.example.backendweride.platform.booking.domain.model.valueobjects.Rating; // <--- Importante
import org.example.backendweride.platform.booking.domain.model.valueobjects.TripEstimate;
import org.example.backendweride.platform.location.domain.model.aggregates.Location;
import org.example.backendweride.platform.location.infrastructure.persistence.jpa.LocationRepository;
import org.example.backendweride.platform.booking.domain.services.BookingCommandService;
import org.example.backendweride.platform.booking.infrastructure.persistence.jpa.BookingRepository;
import org.example.backendweride.platform.garage.infrastructure.persistence.jpa.VehicleRepository;
import org.example.backendweride.platform.iam.infrastructure.persistence.jpa.repositories.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.Optional;

@Service
public class BookingCommandServiceImpl implements BookingCommandService {

    private final BookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;
    private final AccountRepository accountRepository;
    private final LocationRepository locationRepository;

    public BookingCommandServiceImpl(BookingRepository bookingRepository, VehicleRepository vehicleRepository, AccountRepository accountRepository, LocationRepository locationRepository) {
        this.bookingRepository = bookingRepository;
        this.vehicleRepository = vehicleRepository;
        this.accountRepository = accountRepository;
        this.locationRepository = locationRepository;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public Optional<Booking> handle(SetDestinationCommand command) {
        return bookingRepository.findLockedById(command.bookingId()).map(booking -> {
            booking.setDestination(command.lat(), command.lng(), command.address(), estimate(booking, command.lat(), command.lng()));
            return bookingRepository.save(booking);
        });
    }

    @Override
    public Optional<TripEstimate> quoteDestination(String bookingId, Double lat, Double lng) {
        return bookingRepository.findById(bookingId).map(booking -> estimate(booking, lat, lng));
    }

    private TripEstimate estimate(Booking booking, Double lat, Double lng) {
        if (lat == null || lng == null || !Double.isFinite(lat) || !Double.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destino inválido");
        var origin = parseId(booking.getStartLocationId()).flatMap(locationRepository::findById)
                .map(Location::getCoordinates)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "La reserva no tiene una estación de origen válida"));
        if (TripEstimate.straightKm(origin.lat(), origin.lng(), lat, lng) > TripEstimate.MAX_DISTANCE_KM)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Destino fuera de cobertura");
        var vehicle = parseId(booking.getVehicleId()).flatMap(vehicleRepository::findById)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
        return TripEstimate.between(origin.lat(), origin.lng(), lat, lng, vehicle.getType(), vehicle.getPricePerMinute());
    }

    private static Optional<Long> parseId(String id) {
        try { return Optional.of(Long.parseLong(id)); }
        catch (NumberFormatException exception) { return Optional.empty(); }
    }

    /** After a ride to a free point, the vehicle is shown at the station closest to where it was left. */
    private void moveVehicleNearDestination(Booking booking) {
        if (!booking.hasDestination()) return;
        var vehicle = parseId(booking.getVehicleId()).flatMap(vehicleRepository::findById);
        if (vehicle.isEmpty()) return;
        locationRepository.findAll().stream()
                .filter(location -> location.getCoordinates() != null)
                .min(java.util.Comparator.comparingDouble(location -> TripEstimate.straightKm(
                        location.getCoordinates().lat(), location.getCoordinates().lng(), booking.getDestinationLat(), booking.getDestinationLng())))
                .ifPresent(nearest -> {
                    vehicle.get().moveTo(String.valueOf(nearest.getId()));
                    vehicleRepository.save(vehicle.get());
                });
    }

    // 1. Crear Reserva
    @Override
    @org.springframework.transaction.annotation.Transactional
    public Optional<Booking> handle(CreateBookingCommand command) {
        // Serialize reservation creation with account deletion, so an active booking cannot appear after its check.
        final long ownerId;
        try { ownerId = Long.parseLong(command.userId()); }
        catch (NumberFormatException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user id"); }
        accountRepository.findLockedById(ownerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        final long vehicleId;
        try {
            vehicleId = Long.parseLong(command.vehicleId());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid vehicle id");
        }
        var vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
        if ("electric_scooter".equals(vehicle.getType()) && vehicle.getBattery() != null && vehicle.getBattery() < 15)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Batería crítica");
        var booking = new Booking(command);
        // Choque con la reserva de cualquier usuario sobre el mismo vehículo (US22 esc. 2).
        var from = booking.busyFrom();
        var to = command.endDate() != null ? command.endDate() : new Date(from.getTime() + 60_000L);
        if (bookingRepository.findAllByVehicleIdAndStatusIn(command.vehicleId(), Booking.ACTIVE_STATUSES)
                .stream().anyMatch(b -> b.overlaps(from, to)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle is not available in that time range");
        bookingRepository.save(booking);
        return Optional.of(booking);
    }

    // 2. Iniciar Viaje
    @Override
    @org.springframework.transaction.annotation.Transactional
    public Optional<Booking> handle(StartRideCommand command) {
        var bookingOptional = bookingRepository.findLockedById(command.bookingId());

        if (bookingOptional.isPresent()) {
            var booking = bookingOptional.get();
            // Ningún viaje arranca sin destino: el cliente lo fija con PUT /bookings/{id}/destination antes de iniciar.
            if (!booking.hasDestination())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Marca el destino antes de iniciar el viaje");
            booking.startRide();
            bookingRepository.save(booking);
            return Optional.of(booking);
        }

        return Optional.empty();
    }


    @Override
    @org.springframework.transaction.annotation.Transactional
    public Optional<Booking> handle(CompleteBookingCommand command) {
        var bookingOptional = bookingRepository.findLockedById(command.bookingId());

        if (bookingOptional.isPresent()) {
            var booking = bookingOptional.get();


            var rating = command.ratingScore() == null ? null : new Rating(command.ratingScore(), command.ratingComment());


            booking.completeBooking(
                    command.totalCost(),
                    command.discount(),
                    command.distance(),
                    command.duration(),
                    command.averageSpeed(),
                    rating,
                    command.routeCoordinates()
            );
            booking.markRouteSource(command.routeSource());
            moveVehicleNearDestination(booking);

            // Guardamos los cambios
            bookingRepository.save(booking);
            return Optional.of(booking);
        }

        return Optional.empty();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public Optional<Booking> handle(CancelBookingCommand command) {
        return bookingRepository.findLockedById(command.bookingId()).map(booking -> {
            booking.cancel();
            return bookingRepository.save(booking);
        });
    }

    @Override
    public void handle(DeleteBookingCommand command) {
        bookingRepository.deleteById(command.bookingId());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public Optional<Booking> handle(org.example.backendweride.platform.booking.domain.model.commands.RateBookingCommand command) {
        return bookingRepository.findLockedById(command.bookingId()).map(booking -> {
            if (!booking.getUserId().equals(command.userId()))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Resource belongs to another user");
            booking.rate(new Rating(command.score(), command.comment(), command.tags()));
            return bookingRepository.save(booking);
        });
    }
}
