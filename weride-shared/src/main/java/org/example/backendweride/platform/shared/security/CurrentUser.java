package org.example.backendweride.platform.shared.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ownership checks against the authenticated account (ids are opaque strings, the JWT subject;
 * notifications are keyed by username).
 */
public final class CurrentUser {
    private CurrentUser() {}

    public static String id(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return user.id();
    }

    public static void requireSelf(Authentication authentication, String ownerId) {
        if (!id(authentication).equals(ownerId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Resource belongs to another user");
    }

    /** Notifications are keyed by username (preferred_username), not account id. */
    public static void requireUsername(Authentication authentication, String ownerUsername) {
        id(authentication);
        if (!authentication.getName().equals(ownerUsername))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Resource belongs to another user");
    }
}
