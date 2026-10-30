package org.example.backendweride.gateway;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String value = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
        String id =
                value != null && value.matches("[A-Za-z0-9._:-]{1,100}")
                        ? value
                        : UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set("X-Correlation-Id", id);
        return chain.filter(
                exchange.mutate()
                        .request(
                                request ->
                                        request.headers(
                                                headers -> headers.set("X-Correlation-Id", id)))
                        .build());
    }
}
