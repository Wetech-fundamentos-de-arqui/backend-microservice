package org.example.backendweride.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IamSessionClientTest {
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    private final Clock clock = new Clock() {
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };

    private IamSessionClient client(Supplier<ClientResponse> answer) {
        var web = WebClient.builder().baseUrl("http://iam")
                .exchangeFunction(request -> { calls.incrementAndGet(); return Mono.just(answer.get()); })
                .build();
        return new IamSessionClient(web, clock);
    }

    private static ClientResponse json(String body) {
        return ClientResponse.create(HttpStatus.OK).header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(body).build();
    }

    @Test
    void answerIsCachedForThirtySecondsThenAskedAgain() {
        var client = client(() -> json("{\"active\":true}"));
        assertTrue(client.isActive("s1").block());
        assertTrue(client.isActive("s1").block());
        assertEquals(1, calls.get());

        now.set(now.get().plus(Duration.ofSeconds(31)));
        assertTrue(client.isActive("s1").block());
        assertEquals(2, calls.get());
    }

    @Test
    void inactiveSessionIsReportedAndCachedToo() {
        var client = client(() -> json("{\"active\":false}"));
        assertFalse(client.isActive("s1").block());
        assertFalse(client.isActive("s1").block());
        assertEquals(1, calls.get());
    }

    @Test
    void iamErrorIsPropagatedAndNotCached() {
        var failing = new AtomicReference<>(true);
        var client = client(() -> failing.get()
                ? ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build()
                : json("{\"active\":true}"));
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> client.isActive("s1").block());
        failing.set(false);
        assertTrue(client.isActive("s1").block());
        assertEquals(2, calls.get());
    }
}
