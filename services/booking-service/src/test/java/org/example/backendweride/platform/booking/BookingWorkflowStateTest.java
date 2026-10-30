package org.example.backendweride.platform.booking;

import static org.junit.jupiter.api.Assertions.*;

import org.example.backendweride.platform.booking.domain.model.aggregates.Booking;
import org.example.backendweride.platform.booking.domain.model.commands.CreateBookingCommand;
import org.example.backendweride.platform.booking.domain.model.valueobjects.TripEstimate;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Date;

class BookingWorkflowStateTest {
    private Booking booking() {
        return new Booking(
                new CreateBookingCommand(
                        "account-opaque",
                        "vehicle-opaque",
                        "station-opaque",
                        null,
                        null,
                        null,
                        null));
    }

    @Test
    void activeRideKeepsTheVehicleBusyPastItsPlannedEnd() {
        var b =
                new Booking(
                        new CreateBookingCommand(
                                "account",
                                "vehicle",
                                "station",
                                null,
                                Date.from(Instant.now().minusSeconds(120)),
                                Date.from(Instant.now().minusSeconds(60)),
                                null));
        var now = new Date();
        assertFalse(b.overlaps(now, new Date(now.getTime() + 60_000)));
        b.setDestination(-12, -77, "Destination", new TripEstimate(1, 4, 0.5, 2));
        b.stageStart();
        b.confirmStart(Date.from(Instant.now().minusSeconds(60)));
        assertTrue(b.overlaps(now, new Date(now.getTime() + 60_000)));
    }

    @Test
    void billableMinutesRoundUpAndSimulatedRidesCannotBillLessThanElapsed() {
        var b = booking();
        b.setDestination(-12, -77, "Destination", new TripEstimate(1, 4, 0.5, 2));
        b.stageStart();
        var start = new Date();
        b.confirmStart(start);
        assertEquals(1, b.billableMinutes(start, false));
        assertEquals(2, b.billableMinutes(new Date(start.getTime() + 61_000), false));
        assertEquals(4, b.billableMinutes(new Date(start.getTime() + 61_000), true));
        assertEquals(10, b.billableMinutes(new Date(start.getTime() + 600_000), true));
    }

    @Test
    void uncertainStartAndCompletionKeepRecoverableStatesAndRejectDifferentPayloads() {
        var b = booking();
        assertThrows(ResponseStatusException.class, b::stageStart);
        b.setDestination(-12, -77, "Destination", new TripEstimate(1, 4, 0.5, 2));
        b.stageStart();
        assertEquals("starting", b.getStatus());
        var start = new Date();
        b.confirmStart(start);
        assertEquals("in_progress", b.getStatus());
        assertEquals(start, b.getActualStartDate());
        b.stageCompletion("original", "hash");
        b.stageCompletion("original", "hash");
        assertEquals("completing", b.getStatus());
        assertThrows(
                ResponseStatusException.class,
                () -> b.stageCompletion("different", "different-hash"));
        b.completeBooking(2.0, 0.25, 1.0, 4, 15.0, null);
        b.stageCompletion("original", "hash");
        assertEquals("completed", b.getStatus());
        assertThrows(ResponseStatusException.class, b::stageCancel);
    }

    @Test
    void unstartedReservationExpiresFromScheduledStartAndActiveCancellationClosesTheWorkflow() {
        var b =
                new Booking(
                        new CreateBookingCommand(
                                "account",
                                "vehicle",
                                "station",
                                null,
                                Date.from(Instant.now().minusSeconds(901)),
                                null,
                                null));
        assertTrue(b.expired(Instant.now(), 900));
        b.cancel();
        assertFalse(b.expired(Instant.now(), 900));
        var active = booking();
        active.setDestination(-12, -77, null, new TripEstimate(1, 4, 0.5, 2));
        active.stageStart();
        active.confirmStart(new Date());
        active.stageCancel();
        assertEquals("cancelling", active.getStatus());
        active.cancel();
        assertEquals("cancelled", active.getStatus());
    }
}
