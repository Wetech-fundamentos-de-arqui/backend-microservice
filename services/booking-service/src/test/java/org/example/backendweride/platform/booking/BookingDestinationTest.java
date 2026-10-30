package org.example.backendweride.platform.booking;

import static org.junit.jupiter.api.Assertions.*;

import org.example.backendweride.platform.booking.domain.model.aggregates.Booking;
import org.example.backendweride.platform.booking.domain.model.commands.CreateBookingCommand;
import org.example.backendweride.platform.booking.domain.model.valueobjects.TripEstimate;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class BookingDestinationTest {
    // Plaza Mayor -> Parque Kennedy: unos 8,4 km en línea recta.
    private final TripEstimate estimate =
            TripEstimate.between(
                    -12.046374, -77.042793, -12.1205, -77.029833, "electric_scooter", 0.5);

    @Test
    void estimateStretchesDistanceAndRoundsMinutesUp() {
        assertEquals(8.36 * TripEstimate.ROUTE_FACTOR, estimate.distanceKm(), 0.1);
        assertEquals(
                (int) Math.ceil(estimate.distanceKm() / TripEstimate.SCOOTER_KMH * 60),
                estimate.durationMinutes(),
                1);
        assertEquals(estimate.durationMinutes() * 0.5, estimate.estimatedCost(), 0.001);
        assertTrue(
                TripEstimate.between(-12.046374, -77.042793, -12.1205, -77.029833, "bike", 0.5)
                                .durationMinutes()
                        > estimate.durationMinutes());
    }

    @Test
    void destinationIsFreeBeforeTheRideAndChargedAfterItStarts() {
        var booking = new Booking(new CreateBookingCommand("1", "1", "1", "1", null, null, null));
        booking.setDestination(-12.1205, -77.029833, "Parque Kennedy", estimate);
        booking.setDestination(-12.13, -77.03, null, estimate);
        assertEquals(0.0, booking.destinationChangeFeesOrZero());
        booking.startRide();
        booking.setDestination(-12.1205, -77.029833, "Parque Kennedy", estimate);
        booking.setDestination(-12.13, -77.03, "Larcomar", estimate);
        assertEquals(
                2 * TripEstimate.DESTINATION_CHANGE_FEE, booking.destinationChangeFeesOrZero());
        booking.completeBooking(5.0, 1.0, 3.0, 10, 18.0, null);
        assertEquals(6.0, booking.getFinalCost());
        assertThrows(
                ResponseStatusException.class,
                () -> booking.setDestination(-12.1, -77.0, null, estimate));
    }
}
