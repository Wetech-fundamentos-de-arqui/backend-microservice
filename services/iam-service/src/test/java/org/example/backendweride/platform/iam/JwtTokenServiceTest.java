package org.example.backendweride.platform.iam;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.example.backendweride.platform.iam.domain.model.aggregates.Account;
import org.example.backendweride.platform.iam.domain.model.aggregates.AccountSession;
import org.example.backendweride.platform.iam.infrastructure.persistence.jpa.repositories.AccountSessionRepository;
import org.example.backendweride.platform.iam.infrastructure.tokens.jwt.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JwtTokenServiceTest {

    @Test
    void issuesRs256TokenWithSessionAndExpectedClaims() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        var jwk = new RSAKey.Builder((RSAPublicKey) keys.getPublic()).privateKey((RSAPrivateKey) keys.getPrivate()).build();
        var properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri("weride-iam");
        properties.getJwt().setAudiences(List.of("weride-api"));
        var sessions = mock(AccountSessionRepository.class);
        when(sessions.save(any(AccountSession.class))).thenAnswer(call -> call.getArgument(0));
        var service = new JwtTokenService(sessions, new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk))), properties, 7);

        var token = service.generateToken(new Account("ana", "hash"));

        var jwt = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build().decode(token);
        assertEquals("RS256", jwt.getHeaders().get("alg"));
        assertEquals("ana", jwt.getClaimAsString("preferred_username"));
        assertEquals("weride-iam", jwt.getClaimAsString("iss"));
        assertEquals(List.of("weride-api"), jwt.getAudience());
        assertEquals("USER", jwt.getClaimAsString("role"));
        assertNotNull(jwt.getClaimAsString("sid"));
        assertEquals(7 * 24 * 3600L, jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond());
        verify(sessions).save(any(AccountSession.class));
    }
}
