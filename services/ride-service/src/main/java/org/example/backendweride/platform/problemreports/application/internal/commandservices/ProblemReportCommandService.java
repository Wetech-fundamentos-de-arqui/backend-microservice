package org.example.backendweride.platform.problemreports.application.internal.commandservices;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.example.backendweride.platform.problemreports.domain.model.aggregates.ProblemReport;
import org.example.backendweride.platform.problemreports.domain.model.commands.CreateProblemReportCommand;
import org.example.backendweride.platform.problemreports.infrastructure.persistence.jpa.ProblemReportRepository;
import org.example.backendweride.platform.ride.infrastructure.clients.RideDependencies;
import org.example.backendweride.platform.ride.infrastructure.outbox.OutboxService;
import org.example.backendweride.platform.ride.infrastructure.privacy.UserDataGuard;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.util.*;

@Service
public class ProblemReportCommandService {
    private static final Set<String> CATEGORIES =
            Set.of("mechanical", "tires", "gps", "battery", "lock", "other", "brakes", "damage");
    private final ProblemReportRepository reports;
    private final RideDependencies remote;
    private final UserDataGuard guard;
    private final OutboxService outbox;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    public ProblemReportCommandService(
            ProblemReportRepository reports,
            RideDependencies remote,
            UserDataGuard guard,
            OutboxService outbox,
            PlatformTransactionManager manager,
            ObjectMapper json) {
        this.reports = reports;
        this.remote = remote;
        this.guard = guard;
        this.outbox = outbox;
        this.tx = new TransactionTemplate(manager);
        this.json = json;
    }

    public ProblemReport handle(CreateProblemReportCommand c) {
        return handle(c, null);
    }

    public ProblemReport handle(CreateProblemReportCommand c, String key) {
        if (c.categories() == null
                || c.categories().isEmpty()
                || c.categories().size() > CATEGORIES.size()
                || c.categories().stream().anyMatch(Objects::isNull)
                || !CATEGORIES.containsAll(c.categories())
                || c.description() == null
                || c.description().length() > 2000
                || c.bookingId() == null
                || c.bookingId().isBlank()
                || (key != null && (key.isBlank() || key.length() > 128)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid report");
        validatePhoto(c.photo());
        String hash;
        try {
            hash =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(json.writeValueAsBytes(c)));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        var report =
                tx.execute(
                        status -> {
                            guard.assertWritable(c.userId());
                            if (key != null) {
                                var old = reports.findByUserIdAndRequestKey(c.userId(), key);
                                if (old.isPresent()) {
                                    if (!hash.equals(old.get().getPayloadHash()))
                                        throw new ResponseStatusException(
                                                HttpStatus.CONFLICT, "Idempotency-Key reused");
                                    return old.get();
                                }
                            }
                            var b = remote.booking(c.bookingId());
                            if (!c.userId().equals(b.userId()))
                                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                            if (!Objects.equals(c.vehicleId(), b.vehicleId()))
                                throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST, "Vehicle mismatch");
                            boolean recent =
                                    "completed".equals(b.status())
                                            && b.actualEndDate() != null
                                            && System.currentTimeMillis()
                                                            - b.actualEndDate().getTime()
                                                    <= 3600000;
                            if (!Set.of("reserved", "in_progress").contains(b.status()) && !recent)
                                throw new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "Booking is not active or recently completed");
                            remote.vehicle(c.vehicleId());
                            var r =
                                    new ProblemReport(
                                            c.userId(),
                                            c.vehicleId(),
                                            c.bookingId(),
                                            c.categories(),
                                            c.description().trim(),
                                            c.photo(),
                                            false);
                            r.request(key, hash);
                            return reports.saveAndFlush(r);
                        });
        process(report.getId());
        return reports.findById(report.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE));
    }

    public void process(String id) {
        var found = reports.findById(id);
        if (found.isEmpty() || "done".equals(found.get().getProcessingStatus())) return;
        var r = found.get();
        if (!r.isBookingChecked()) {
            boolean waived =
                    remote.fault(
                            r.getBookingId(),
                            r.getUserId(),
                            r.getVehicleId(),
                            r.getId(),
                            r.getReportDate().toInstant());
            tx.executeWithoutResult(
                    status -> {
                        guard.assertWritable(r.getUserId());
                        reports.lock(id)
                                .ifPresent(
                                        current -> {
                                            current.bookingChecked(waived);
                                            reports.save(current);
                                        });
                    });
        }
        remote.maintenance(r.getVehicleId());
        tx.executeWithoutResult(
                status -> {
                    guard.assertWritable(r.getUserId());
                    reports.lock(id)
                            .ifPresent(
                                    current -> {
                                        if (!"done".equals(current.getProcessingStatus())) {
                                            current.garageUpdated();
                                            current.processed();
                                            outbox.enqueue(
                                                    "ride.problem-reported.v1",
                                                    current.getUserId(),
                                                    "Reporte recibido",
                                                    current.isChargeWaived()
                                                            ? "Reporte recibido. El viaje finalizó"
                                                                  + " sin cobro."
                                                            : "Tu reporte fue enviado a"
                                                                  + " mantenimiento.",
                                                    current.getId(),
                                                    "problem_report");
                                            reports.save(current);
                                        }
                                    });
                });
    }

    @Scheduled(fixedDelayString = "${weride.ride.incident-retry-ms:5000}", initialDelay = 5000)
    public void retry() {
        for (var r : reports.findTop50ByProcessingStatusOrderByReportDateAsc("pending"))
            try {
                process(r.getId());
            } catch (RuntimeException ignored) {
            }
    }

    static void validatePhoto(String photo) {
        if (photo == null || photo.isBlank()) return;
        if (photo.length() > 1_400_000
                || !photo.matches("^data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$"))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Photo must be JPEG, PNG or WebP up to 1 MB");
        try {
            byte[] bytes = Base64.getDecoder().decode(photo.substring(photo.indexOf(',') + 1));
            boolean jpeg =
                    bytes.length > 3
                            && bytes[0] == (byte) 0xff
                            && bytes[1] == (byte) 0xd8
                            && bytes[2] == (byte) 0xff;
            boolean png =
                    bytes.length > 8
                            && bytes[0] == (byte) 0x89
                            && bytes[1] == 0x50
                            && bytes[2] == 0x4e
                            && bytes[3] == 0x47
                            && bytes[4] == 13
                            && bytes[5] == 10
                            && bytes[6] == 26
                            && bytes[7] == 10;
            boolean webp =
                    bytes.length > 12
                            && bytes[0] == 82
                            && bytes[1] == 73
                            && bytes[2] == 70
                            && bytes[3] == 70
                            && bytes[8] == 87
                            && bytes[9] == 69
                            && bytes[10] == 66
                            && bytes[11] == 80;
            if (bytes.length > 1_048_576
                    || !(photo.startsWith("data:image/jpeg;") && jpeg
                            || photo.startsWith("data:image/png;") && png
                            || photo.startsWith("data:image/webp;") && webp))
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image");
        }
    }
}
