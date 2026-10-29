package org.example.backendweride.platform.iam.infrastructure.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.example.backendweride.platform.shared.security.InternalTokenFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * iam chain (weride.security.shared=false): same resource server as the shared one, plus the public
 * sign-in / sign-up routes, where a stale Authorization header must not turn into a 401.
 */
@Configuration
public class IamSecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, InternalTokenFilter internal,
                                    Converter<Jwt, AbstractAuthenticationToken> converter) throws Exception {
        BearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
        BearerTokenResolver resolver = (HttpServletRequest request) ->
                request.getRequestURI().startsWith("/api/v1/authentication/") ? null : defaultResolver.resolve(request);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)          // CORS is handled by the gateway
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/v1/authentication/**",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .bearerTokenResolver(resolver)
                        .jwt(j -> j.jwtAuthenticationConverter(converter)))
                .addFilterBefore(internal, BearerTokenAuthenticationFilter.class)
                .build();
    }
}
