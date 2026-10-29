package org.example.backendweride.platform.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class JwtSecurityConfigTest {

    @Test
    void converterBuildsAuthenticatedUserFromSubAndPreferredUsername() {
        var jwt = Jwt.withTokenValue("t").header("alg", "RS256")
                .subject("acc-7").claim("preferred_username", "ana")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

        var auth = new JwtSecurityConfig().jwtAuthenticationConverter().convert(jwt);

        assertEquals(new AuthenticatedUser("acc-7", "ana"), auth.getPrincipal());
        assertEquals("ana", auth.getName());
        assertEquals("acc-7", CurrentUser.id(auth));
    }
}
