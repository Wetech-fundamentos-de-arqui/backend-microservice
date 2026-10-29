package org.example.backendweride.platform.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtDecoderTest {
    private static KeyPair keys;
    private static KeyPair otherKeys;

    @TempDir
    Path dir;

    @BeforeAll
    static void generateKeys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        otherKeys = generator.generateKeyPair();
    }

    private JwtDecoder decoder() throws Exception {
        Path pem = dir.resolve("public.pem");
        Files.writeString(pem, "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keys.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n");
        var properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setPublicKeyLocation(new FileSystemResource(pem));
        properties.getJwt().setIssuerUri("weride-iam");
        properties.getJwt().setAudiences(List.of("weride-api"));
        return new JwtSecurityConfig().jwtDecoder(properties);
    }

    private static String token(KeyPair signer, String issuer, String audience) {
        var jwk = new RSAKey.Builder((RSAPublicKey) signer.getPublic()).privateKey((RSAPrivateKey) signer.getPrivate()).build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        var claims = JwtClaimsSet.builder().subject("acc-7").claim("preferred_username", "ana")
                .issuer(issuer).audience(List.of(audience))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Test
    void validTokenIsDecoded() throws Exception {
        var jwt = decoder().decode(token(keys, "weride-iam", "weride-api"));
        assertEquals("acc-7", jwt.getSubject());
    }

    @Test
    void differentIssuerIsRejected() throws Exception {
        var decoder = decoder();
        assertThrows(JwtValidationException.class, () -> decoder.decode(token(keys, "other", "weride-api")));
    }

    @Test
    void differentAudienceIsRejected() throws Exception {
        var decoder = decoder();
        assertThrows(JwtValidationException.class, () -> decoder.decode(token(keys, "weride-iam", "other-api")));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        var decoder = decoder();
        assertThrows(JwtException.class, () -> decoder.decode(token(otherKeys, "weride-iam", "weride-api")));
    }
}
