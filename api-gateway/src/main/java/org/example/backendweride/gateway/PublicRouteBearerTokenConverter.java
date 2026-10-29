package org.example.backendweride.gateway;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Ignores the Authorization header on the public authentication routes.
 */
public class PublicRouteBearerTokenConverter implements ServerAuthenticationConverter {
    private final ServerAuthenticationConverter delegate = new ServerBearerTokenAuthenticationConverter();

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        if (exchange.getRequest().getURI().getPath().startsWith("/api/v1/authentication/")) return Mono.empty();
        return delegate.convert(exchange);
    }
}
