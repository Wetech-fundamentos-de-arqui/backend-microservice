package org.example.backendweride.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.List;

/**
 * Entry point security: CORS lives only here, the JWT is verified with the iam public key (RS256, iss + aud),
 * the session is checked against iam, and /internal/** is never reachable from outside.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {
    private static final String[] SWAGGER_PATHS =
            {"/swagger-ui.html", "/swagger-ui/**", "/webjars/**", "/v3/api-docs/**", "/api-docs/**"};

    @Bean
    SecurityWebFilterChain filterChain(ServerHttpSecurity http, SessionActivityFilter sessionFilter,
                                       CorsConfigurationSource cors,
                                       @Value("${WERIDE_SWAGGER_ENABLED:false}") boolean swaggerEnabled) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(c -> c.configurationSource(cors))
                .authorizeExchange(a -> a
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/internal/**").denyAll()
                        .pathMatchers("/api/v1/authentication/**", "/actuator/health").permitAll()
                        .pathMatchers(swaggerEnabled ? SWAGGER_PATHS : new String[] {"/api/v1/authentication/**"}).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(o -> o
                        // a stale token on the public sign-in/sign-up must not turn into a 401
                        .bearerTokenConverter(new PublicRouteBearerTokenConverter())
                        .jwt(Customizer.withDefaults()))
                .addFilterAfter(sessionFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(OAuth2ResourceServerProperties properties) throws IOException {
        var jwt = properties.getJwt();
        RSAPublicKey key;
        try (InputStream pem = jwt.getPublicKeyLocation().getInputStream()) {
            key = RsaKeyConverters.x509().convert(pem);
        }
        var decoder = NimbusReactiveJwtDecoder.withPublicKey(key).build();
        var audiences = jwt.getAudiences();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(jwt.getIssuerUri()),
                new JwtClaimValidator<List<String>>("aud",
                        aud -> aud != null && aud.stream().anyMatch(audiences::contains))));
        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${weride.cors.allowed-origins}") List<String> origins) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    IamSessionClient iamSessionClient(WebClient.Builder builder,
                                      @Value("${IAM_BASE_URL:http://localhost:8081}") String iamBaseUrl,
                                      @Value("${weride.internal.service-name}") String serviceName,
                                      @Value("${weride.internal.targets.iam.secret}") String iamSecret) {
        var iam = builder.baseUrl(iamBaseUrl)
                .defaultHeader("X-Service-Name", serviceName)
                .defaultHeader("X-Internal-Token", iamSecret)
                .build();
        return new IamSessionClient(iam, Clock.systemUTC());
    }

    @Bean
    SessionActivityFilter sessionActivityFilter(IamSessionClient sessions) {
        return new SessionActivityFilter(sessions);
    }
}
