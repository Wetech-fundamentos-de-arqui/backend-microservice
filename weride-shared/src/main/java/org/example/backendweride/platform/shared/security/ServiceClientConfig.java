package org.example.backendweride.platform.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * RestClient defaults for service-to-service calls: short timeouts and the caller's service name.
 * The per-receiver secret (X-Internal-Token) is set by each service on its own client.
 */
@Configuration
public class ServiceClientConfig {

    @Bean
    RestClientCustomizer werideClients(@Value("${weride.internal.service-name:}") String serviceName) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        return builder -> builder
                .requestFactory(factory)
                .defaultHeader("X-Service-Name", serviceName);
    }
}
