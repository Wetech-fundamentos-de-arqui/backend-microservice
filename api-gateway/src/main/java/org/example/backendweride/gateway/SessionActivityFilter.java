package org.example.backendweride.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Rejects requests whose token carries a revoked session: 401 when iam says inactive (or the token has no sid),
 * 503 when iam cannot answer (fail closed). Requests without a verified token (public routes) pass untouched.
 */
public class SessionActivityFilter implements WebFilter {
    private enum Check { ACTIVE, REVOKED, IAM_DOWN }

    private final IamSessionClient sessions;

    public SessionActivityFilter(IamSessionClient sessions) {
        this.sessions = sessions;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> Optional.ofNullable(context.getAuthentication()))
                .defaultIfEmpty(Optional.empty())
                .flatMap(auth -> auth.map(Authentication::getPrincipal)
                        .filter(Jwt.class::isInstance)
                        .map(principal -> check((Jwt) principal)
                                .flatMap(result -> switch (result) {
                                    case ACTIVE -> chain.filter(exchange);
                                    case REVOKED -> reject(exchange, HttpStatus.UNAUTHORIZED);
                                    case IAM_DOWN -> reject(exchange, HttpStatus.SERVICE_UNAVAILABLE);
                                }))
                        .orElseGet(() -> chain.filter(exchange)));
    }

    private Mono<Check> check(Jwt jwt) {
        String sid = jwt.getClaimAsString("sid");
        if (sid == null) return Mono.just(Check.REVOKED);
        return sessions.isActive(sid)
                .map(active -> active ? Check.ACTIVE : Check.REVOKED)
                .onErrorReturn(Check.IAM_DOWN);
    }

    private static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }
}
