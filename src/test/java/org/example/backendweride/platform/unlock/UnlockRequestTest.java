package org.example.backendweride.platform.unlock;

import org.example.backendweride.platform.unlock.domain.model.UnlockRequest;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class UnlockRequestTest {
    @Test
    void codesAreBoundToTheRequestAndFailedRequestsCanRetry() {
        var request = new UnlockRequest("42", "7", "booking-id", Instant.now(), "qr_code");
        assertEquals("pending", request.getStatus());
        assertTrue(request.getAccessCode().matches("[A-HJ-NP-Z2-9]{6}"));
        request.attempt("qr_code", "weride:vehicle:8", Instant.now());
        assertEquals("failed", request.getStatus());
        assertNull(request.getActualUnlockTime());
        request.attempt("manual", "WRONG1", Instant.now());
        assertEquals("failed", request.getStatus());
        request.attempt("manual", " " + request.getAccessCode().toLowerCase() + " ", Instant.now());
        assertEquals("unlocked", request.getStatus());
        assertNotNull(request.getActualUnlockTime());
        assertEquals(3, request.getAttempts());
        assertNull(request.getErrorMessage());
        var qr = new UnlockRequest("42", "7", "booking-id", Instant.now(), "qr_code");
        qr.attempt("qr_code", "weride:vehicle:7", Instant.now());
        assertEquals("unlocked", qr.getStatus());
    }

    @Test
    void manualCodeExpiresFifteenMinutesAfterTheLaterOfRequestAndScheduledStart() {
        var immediate = new UnlockRequest("42", "7", "booking-id", Instant.now(), "manual");
        assertEquals(immediate.getRequestedAt().plus(UnlockRequest.CODE_TTL), immediate.getCodeExpiresAt());
        immediate.attempt("manual", immediate.getAccessCode(), immediate.getCodeExpiresAt().plusSeconds(1));
        assertEquals("failed", immediate.getStatus());
        assertTrue(immediate.getErrorMessage().contains("caducó"));

        var start = Instant.now().plusSeconds(3600);
        var scheduled = new UnlockRequest("42", "7", "booking-id", start, "manual");
        assertEquals(start.plus(UnlockRequest.CODE_TTL), scheduled.getCodeExpiresAt());
        scheduled.attempt("manual", scheduled.getAccessCode(), start.plusSeconds(60));
        assertEquals("unlocked", scheduled.getStatus());
    }
}
