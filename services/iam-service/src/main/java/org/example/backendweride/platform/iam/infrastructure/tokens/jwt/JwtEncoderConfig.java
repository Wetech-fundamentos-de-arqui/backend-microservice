package org.example.backendweride.platform.iam.infrastructure.tokens.jwt;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * RS256 signer. The private key (PKCS#8 PEM) is only configured in iam and comes from the environment.
 */
@Configuration
public class JwtEncoderConfig {

    @Bean
    JwtEncoder jwtEncoder(@Value("${weride.iam.jwt.private-key-location}") Resource privateKeyLocation,
                          OAuth2ResourceServerProperties properties) throws IOException {
        RSAPrivateKey privateKey;
        RSAPublicKey publicKey;
        try (InputStream pem = privateKeyLocation.getInputStream()) {
            privateKey = RsaKeyConverters.pkcs8().convert(pem);
        }
        try (InputStream pem = properties.getJwt().getPublicKeyLocation().getInputStream()) {
            publicKey = RsaKeyConverters.x509().convert(pem);
        }
        var jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }
}
