package org.example.backendweride.platform.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** Keeps one bounded diagnostic identifier across HTTP boundaries without logging credentials. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String value = request.getHeader("X-Correlation-Id");
        String id =
                value != null && value.matches("[A-Za-z0-9._:-]{1,100}")
                        ? value
                        : UUID.randomUUID().toString();
        response.setHeader("X-Correlation-Id", id);
        try (var ignored = MDC.putCloseable("correlationId", id)) {
            chain.doFilter(request, response);
        }
    }
}
