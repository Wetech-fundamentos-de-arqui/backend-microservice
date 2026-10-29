package org.example.backendweride.platform.shared.security;

import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.converter.RsaKeyConverters;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

/**
 * RS256 verification with the iam public key (iss + aud checked) and mapping of the token to AuthenticatedUser.
 * Boot's own decoder for public-key-location skips the issuer check, hence the explicit bean.
 */
@Configuration
public class JwtSecurityConfig {

    @Bean
    JwtDecoder jwtDecoder(OAuth2ResourceServerProperties properties) throws IOException {
        var jwt = properties.getJwt();
        RSAPublicKey key;
        try (InputStream pem = jwt.getPublicKeyLocation().getInputStream()) {
            key = RsaKeyConverters.x509().convert(pem);
        }
        var decoder = NimbusJwtDecoder.withPublicKey(key).build();
        List<String> audiences = jwt.getAudiences();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(jwt.getIssuerUri()),
                audienceValidator(audiences)));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(List<String> audiences) {
        return new JwtClaimValidator<List<String>>("aud",
                aud -> aud != null && aud.stream().anyMatch(audiences::contains));
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> {
            var user = new AuthenticatedUser(jwt.getSubject(), jwt.getClaimAsString("preferred_username"));
            return new UsernamePasswordAuthenticationToken(user, jwt, List.of()) {
                @Override public String getName() { return user.username(); }
            };
        };
    }
}
