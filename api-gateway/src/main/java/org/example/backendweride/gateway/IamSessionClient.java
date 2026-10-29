package org.example.backendweride.gateway;

import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asks iam whether a session (sid) is still active, caching each answer for at most 30 seconds (ADR-004).
 * An error from iam is never cached and is propagated, so callers fail closed.
 */
public class IamSessionClient {
    static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final int PURGE_THRESHOLD = 1000;

    private record Entry(boolean active, Instant expiresAt) {}

    private final WebClient iam;
    private final Clock clock;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public IamSessionClient(WebClient iam, Clock clock) {
        this.iam = iam;
        this.clock = clock;
    }

    public Mono<Boolean> isActive(String sid) {
        var cached = cache.get(sid);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) return Mono.just(cached.active());
        return iam.get().uri("/internal/v1/sessions/{sid}", sid)
                .retrieve()
                .bodyToMono(SessionStatus.class)
                .timeout(TIMEOUT)
                .map(SessionStatus::active)
                .doOnNext(active -> remember(sid, active));
    }

    private void remember(String sid, boolean active) {
        var now = clock.instant();
        if (cache.size() > PURGE_THRESHOLD) cache.values().removeIf(entry -> !entry.expiresAt().isAfter(now));
        cache.put(sid, new Entry(active, now.plus(CACHE_TTL)));
    }

    record SessionStatus(boolean active) {}
}
