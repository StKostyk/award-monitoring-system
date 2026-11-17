package ua.edu.chnu.awards.auth.service;

import java.time.Duration;
import java.util.List;

import ua.edu.chnu.awards.auth.config.AuthProperties;

final class AuthPropertiesFixture {

    private AuthPropertiesFixture() {
    }

    static AuthProperties properties() {
        return new AuthProperties("http://localhost:8080", "http://localhost:4200",
            List.of(), List.of("chnu.edu.ua"), Duration.ofHours(24), Duration.ofHours(1), Duration.ofHours(24),
            Duration.ofHours(1), Duration.ofMinutes(1),
            new AuthProperties.Client("award-web", List.of(), List.of(), Duration.ofMinutes(15), Duration.ofDays(7)),
            new AuthProperties.Jwk("", "", "", ""));
    }
}
