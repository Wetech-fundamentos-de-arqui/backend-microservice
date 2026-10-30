package org.example.backendweride.platform.shared.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ownership checks against the authenticated account (ids are opaque strings, the JWT subject;
 * resource owners are keyed by the stable account id).
 */
public final class CurrentUser {
    private CurrentUser() {}

    public static void requireService(Authentication authentication, String... allowed) {
        if (authentication == null || !authentication.isAuthenticated()
                || java.util.Arrays.stream(allowed).noneMatch(name -> ("service:" + name).equals(authentication.getName())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Service is not allowed for this operation");
    }

    /** Catalog writes (fleet, stations, plans) are for administrators only. */
    public static void requireAdmin(Authentication authentication) {
        id(authentication);
        if (authentication.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role required");
    }

    public static String id(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return user.id();
    }

    public static void requireSelf(Authentication authentication, String ownerId) {
        if (!id(authentication).equals(ownerId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Resource belongs to another user");
    }

    /** Compare a username only for contracts explicitly keyed by username. */
    public static void requireUsername(Authentication authentication, String ownerUsername) {
        id(authentication);
        if (!authentication.getName().equals(ownerUsername))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Resource belongs to another user");
    }
}
