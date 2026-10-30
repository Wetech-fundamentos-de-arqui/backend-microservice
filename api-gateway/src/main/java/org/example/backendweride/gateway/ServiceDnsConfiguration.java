package org.example.backendweride.gateway;

import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/** Re-resolve service names after Docker replaces a container's network endpoint. */
@Configuration
public class ServiceDnsConfiguration {
    private HttpClient serviceDns(HttpClient client) {
        return client.resolver(resolver -> resolver
                .cacheMinTimeToLive(Duration.ZERO)
                .cacheMaxTimeToLive(Duration.ofSeconds(5))
                .cacheNegativeTimeToLive(Duration.ZERO)
                .queryTimeout(Duration.ofSeconds(2)));
    }

    @Bean
    HttpClientCustomizer routingServiceDns() {
        return this::serviceDns;
    }

    @Bean
    WebClientCustomizer sessionServiceDns() {
        return builder -> builder.clientConnector(
                new ReactorClientHttpConnector(serviceDns(HttpClient.create())));
    }
}
