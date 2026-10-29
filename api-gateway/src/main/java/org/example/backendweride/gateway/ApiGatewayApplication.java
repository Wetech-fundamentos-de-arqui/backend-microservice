package org.example.backendweride.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.net.UnknownHostException;

@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    /** A service that is down or not deployed yet answers 503 instead of the gateway's default 500. */
    @Bean
    GlobalFilter downstreamUnavailableFilter() {
        return (exchange, chain) -> chain.filter(exchange).onErrorMap(
                e -> e instanceof ConnectException || e instanceof UnknownHostException,
                e -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable", e));
    }

}
