package org.example.backendweride.platform.iam.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.example.backendweride.platform.iam.application.internal.commandservices.AccountDeletionService;
import org.example.backendweride.platform.iam.application.internal.commandservices.AccountSecurityService;
import org.example.backendweride.platform.shared.security.CurrentUser;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/accounts/me")
@Tag(name = "Account security")
public class AccountSecurityController {
    @io.swagger.v3.oas.annotations.media.Schema(description = "Current password and replacement password; never persisted as plain text")
    public record PasswordChange(@NotBlank String currentPassword, @NotBlank String newPassword) {}
    @io.swagger.v3.oas.annotations.media.Schema(description = "Password confirmation for permanent account deletion")
    public record DeleteAccount(@NotBlank String password) {}
    private final AccountSecurityService security;
    private final AccountDeletionService deletion;
    private final JwtDecoder decoder;
    private final DefaultBearerTokenResolver bearer = new DefaultBearerTokenResolver();

    public AccountSecurityController(AccountSecurityService security, AccountDeletionService deletion, JwtDecoder decoder) {
        this.security = security;
        this.deletion = deletion;
        this.decoder = decoder;
    }

    private static Long id(Authentication authentication) { return Long.valueOf(CurrentUser.id(authentication)); }

    // Spring erases the authentication credentials after login, so the sid is read from the (already verified) bearer token
    private String sid(HttpServletRequest request) {
        return decoder.decode(bearer.resolve(request)).getClaimAsString("sid");
    }

    @PutMapping("/password")
    @Operation(summary = "Change password and revoke other devices", description = "Requires the current password. The new password must contain 8 to 64 characters and fit the BCrypt 72-byte limit. Keeps the current session.")
    @ApiResponse(responseCode = "400", description = "CURRENT_PASSWORD_INVALID or NEW_PASSWORD_INVALID")
    public ResponseEntity<Void> password(@Valid @RequestBody PasswordChange body, Authentication authentication, HttpServletRequest request) {
        security.changePassword(id(authentication), sid(request), body.currentPassword(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/revoke-others")
    @Operation(summary = "Revoke other active sessions", description = "Returns the number revoked, including zero when no other unexpired sessions exist.")
    public Map<String, Integer> revoke(Authentication authentication, HttpServletRequest request) {
        return Map.of("revoked", security.revokeOthers(id(authentication), sid(request)));
    }

    @PostMapping("/sessions/logout")
    @Operation(summary = "Revoke the current session")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest request) {
        security.logout(id(authentication), sid(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/delete")
    @Operation(summary = "Start deleting the own account", description = "Requires the password. Creates (or returns) the persisted deletion workflow of ADR-008, marks the account DELETION_PENDING and revokes its sessions; the other services erase their personal data asynchronously.")
    @ApiResponse(responseCode = "202", description = "Deletion workflow accepted; body has the workflowId")
    @ApiResponse(responseCode = "400", description = "CURRENT_PASSWORD_INVALID")
    @ApiResponse(responseCode = "409", description = "ACTIVE_BOOKINGS: finish or cancel all active bookings first")
    @ApiResponse(responseCode = "503", description = "BOOKING_UNAVAILABLE: nothing was changed, retry later")
    public ResponseEntity<Map<String, String>> delete(@Valid @RequestBody DeleteAccount body, Authentication authentication, HttpServletRequest request) {
        var workflowId = deletion.requestDeletion(id(authentication), sid(request), body.password());
        return ResponseEntity.accepted().body(Map.of("workflowId", workflowId));
    }
}
