package org.example.backendweride.platform.unlock.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "unlock_requests")
@Getter
@NoArgsConstructor
public class UnlockRequest {
    public static final Duration CODE_TTL = Duration.ofMinutes(15);
    // Sin 0/O ni 1/I: el usuario copia el código a mano.
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Id private String id;
    private String userId;
    private String vehicleId;
    private String bookingId;
    private Instant requestedAt;
    private Instant scheduledUnlockTime;
    private Instant actualUnlockTime;
    private String status;
    private String method;
    private String unlockCode = "";
    private int attempts;
    private String errorMessage;
    // Código de un solo uso generado por el servidor; null en solicitudes anteriores a esta columna.
    private String accessCode;
    private Instant codeExpiresAt;

    public UnlockRequest(String userId, String vehicleId, String bookingId, Instant scheduledUnlockTime, String method) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.vehicleId = vehicleId;
        this.bookingId = bookingId;
        this.requestedAt = Instant.now();
        this.scheduledUnlockTime = scheduledUnlockTime;
        this.method = method;
        this.status = "pending";
        this.accessCode = RANDOM.ints(6, 0, CODE_ALPHABET.length())
                .collect(StringBuilder::new, (sb, i) -> sb.append(CODE_ALPHABET.charAt(i)), StringBuilder::append).toString();
        // Una reserva programada no se puede desbloquear antes de su hora, así que el plazo corre desde ese momento.
        var validFrom = scheduledUnlockTime != null && scheduledUnlockTime.isAfter(requestedAt) ? scheduledUnlockTime : requestedAt;
        this.codeExpiresAt = validFrom.plus(CODE_TTL);
    }

    public void attempt(String method, String code, Instant now) {
        this.method = method;
        this.unlockCode = code;
        this.attempts++;
        boolean manual = "manual".equals(method);
        boolean expired = manual && (codeExpiresAt == null || now.isAfter(codeExpiresAt));
        boolean valid = "qr_code".equals(method) ? ("weride:vehicle:" + vehicleId).equals(code)
                : manual && !expired && accessCode != null && accessCode.equalsIgnoreCase(code.trim());
        this.status = valid ? "unlocked" : "failed";
        this.actualUnlockTime = valid ? now : null;
        this.errorMessage = valid ? null : expired ? "El código caducó. Genera uno nuevo desde tu reserva."
                : "Código no reconocido. Revisa el código de tu reserva o reintenta el QR.";
    }
}
