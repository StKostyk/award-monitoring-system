package ua.edu.chnu.awards.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

class AuthPropertiesTest {

    private final AuthProperties properties = new AuthProperties("http://localhost:8080", "http://localhost:4200",
        List.of(), List.of("chnu.edu.ua"), Duration.ofHours(24), Duration.ofHours(1), Duration.ofHours(24),
        Duration.ofHours(1), Duration.ofMinutes(1),
        new AuthProperties.Client("award-web", List.of(), List.of(), Duration.ofMinutes(15), Duration.ofDays(7)),
        new AuthProperties.Jwk("", "", "", ""));

    @Test
    void anAwardLinkPointsToTheAwardPageOfTheBrowserApplication() {
        assertThat(properties.awardLink(5L)).isEqualTo("http://localhost:4200/awards/5");
    }

    @Test
    void aTokenLinkCarriesTheTokenAsAQueryParameter() {
        assertThat(properties.link("/verify-email", "abc")).isEqualTo("http://localhost:4200/verify-email?token=abc");
    }
}
