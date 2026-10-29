package org.example.backendweride.platform.iam.infrastructure.deletion;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * weride.internal.targets.&lt;service&gt;.url / .secret: where iam calls and with which secret.
 */
@ConfigurationProperties(prefix = "weride.internal")
public record InternalTargetsProperties(Map<String, Target> targets) {
    public record Target(String url, String secret) {}

    public InternalTargetsProperties {
        targets = targets == null ? Map.of() : targets;
    }
}
