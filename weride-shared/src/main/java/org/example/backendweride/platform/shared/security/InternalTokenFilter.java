package org.example.backendweride.platform.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * Guards service-to-service endpoints (/internal/v1/**): each caller proves its identity with
 * X-Service-Name + X-Internal-Token, checked against the secret configured for that caller.
 */
public class InternalTokenFilter extends OncePerRequestFilter {
    private final Map<String, String> callers;

    public InternalTokenFilter(Map<String, String> callers) {
        this.callers = callers;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return !req.getRequestURI().startsWith("/internal/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String caller = req.getHeader("X-Service-Name");
        String secret = caller == null ? null : callers.get(caller);
        String token = req.getHeader("X-Internal-Token");
        if (secret == null || token == null || !MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("service:" + caller, null, List.of()));
        chain.doFilter(req, res);
    }
}
