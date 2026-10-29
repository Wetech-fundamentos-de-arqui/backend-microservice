package org.example.backendweride.gateway;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewaySecurityTest {
    private static KeyPair keys;
    private static Path publicPem;

    @TempDir
    static Path dir;

    @BeforeAll
    static void generateKeys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        publicPem = dir.resolve("public.pem");
        Files.writeString(publicPem, "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keys.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n");
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.public-key-location", () -> "file:" + publicPem);
        registry.add("INTERNAL_SECRET_API_GATEWAY_TO_IAM", () -> "test-secret");
        registry.add("PLAN_BASE_URL", () -> "http://localhost:1");   // nothing listens there: the plan service is down
    }

    @LocalServerPort int port;
    @Autowired WebTestClient web;
    @MockitoBean IamSessionClient sessions;

    private static String token(String issuer, String audience) {
        var jwk = new RSAKey.Builder((RSAPublicKey) keys.getPublic()).privateKey((RSAPrivateKey) keys.getPrivate()).build();
        var claims = JwtClaimsSet.builder().subject("1").claim("preferred_username", "ana").claim("sid", "sid-1")
                .issuer(issuer).audience(List.of(audience))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)))
                .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }

    @Test
    void protectedRouteWithoutTokenIsUnauthorized() {
        web.get().uri("/api/v1/plans").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void tokenWithWrongIssuerOrAudienceIsUnauthorized() {
        web.get().uri("/api/v1/plans").headers(h -> h.setBearerAuth(token("other", "weride-api"))).exchange().expectStatus().isUnauthorized();
        web.get().uri("/api/v1/plans").headers(h -> h.setBearerAuth(token("weride-iam", "other"))).exchange().expectStatus().isUnauthorized();
    }

    @Test
    void revokedSessionIsUnauthorized() {
        when(sessions.isActive(anyString())).thenReturn(Mono.just(false));
        web.get().uri("/api/v1/plans").headers(h -> h.setBearerAuth(token("weride-iam", "weride-api"))).exchange().expectStatus().isUnauthorized();
    }

    @Test
    void iamDownFailsClosed() {
        when(sessions.isActive(anyString())).thenReturn(Mono.error(new RuntimeException("iam down")));
        web.get().uri("/api/v1/plans").headers(h -> h.setBearerAuth(token("weride-iam", "weride-api"))).exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void validTokenIsRoutedOnAndDownServiceIsUnavailable() {
        when(sessions.isActive("sid-1")).thenReturn(Mono.just(true));
        web.get().uri("/api/v1/plans").headers(h -> h.setBearerAuth(token("weride-iam", "weride-api"))).exchange()
                .expectStatus().isEqualTo(503);   // passed security; the plan service is down
    }

    @Test
    void internalPathsAreNeverReachable() {
        when(sessions.isActive("sid-1")).thenReturn(Mono.just(true));
        web.get().uri("/internal/v1/sessions/x").headers(h -> h.setBearerAuth(token("weride-iam", "weride-api"))).exchange()
                .expectStatus().isForbidden();
        web.get().uri("/internal/v1/sessions/x").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void signInIsPublicEvenWithStaleToken() {
        web.post().uri("/api/v1/authentication/sign-in").headers(h -> h.setBearerAuth("garbage")).exchange()
                .expectStatus().value(status -> assertNotEquals(401, status));
    }

    @Test
    void corsPreflightIsAnsweredByTheGateway() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/plans"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "GET")
                .build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
        assertEquals(200, response.statusCode());
        assertEquals("http://localhost:4200", response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
    }
}
