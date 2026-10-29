package org.example.backendweride.platform.shared.security;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * Exposes InternalTokenFilter as a bean (also when weride.security.shared=false, iam reuses it)
 * but keeps it out of the servlet chain: it only runs inside the Spring Security chain.
 */
@Configuration
public class SecurityFiltersConfig {

    @Bean
    InternalTokenFilter internalTokenFilter(Environment environment) {
        Map<String, String> callers = Binder.get(environment)
                .bind("weride.internal.callers", Bindable.mapOf(String.class, String.class))
                .orElse(Map.of());
        return new InternalTokenFilter(callers);
    }

    @Bean
    FilterRegistrationBean<InternalTokenFilter> internalFilterRegistration(InternalTokenFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
