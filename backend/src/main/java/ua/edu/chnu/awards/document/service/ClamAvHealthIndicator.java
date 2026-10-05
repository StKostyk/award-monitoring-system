package ua.edu.chnu.awards.document.service;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * The {@code clamav} component of the actuator health: up while {@code clamd} answers a ping. Uploads are refused
 * while it is down, so the application reports itself down too.
 */
@Component("clamavHealthIndicator")
@ConditionalOnProperty(name = "app.documents.scan.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ClamAvHealthIndicator implements HealthIndicator {

    private final ClamAvScanner scanner;

    @Override
    public Health health() {
        return scanner.ping() ? Health.up().build() : Health.down().build();
    }
}
