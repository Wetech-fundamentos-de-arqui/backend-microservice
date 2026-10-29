package org.example.backendweride.platform.iam.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.backendweride.platform.iam.application.internal.commandservices.AccountSecurityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Internal API (service identity required, never routed by the gateway).
 */
@RestController
@RequestMapping("/internal/v1/sessions")
@Tag(name = "Internal sessions")
public class InternalSessionsController {
    private final AccountSecurityService security;

    public InternalSessionsController(AccountSecurityService security) {
        this.security = security;
    }

    @GetMapping("/{sid}")
    @Operation(summary = "Is the session still active?", description = "Unknown, revoked or expired sessions and accounts in DELETION_PENDING answer active=false.")
    public Map<String, Boolean> status(@PathVariable String sid) {
        return Map.of("active", security.isSessionActive(sid));
    }
}
