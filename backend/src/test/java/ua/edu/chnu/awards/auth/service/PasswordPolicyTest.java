package ua.edu.chnu.awards.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void ac21_acceptsTenCharactersUpToSeventyTwoBytes() {
        assertThat(policy.problem("abcdefghij")).isEmpty();
        assertThat(policy.problem("a".repeat(72))).isEmpty();
        assertThat(policy.problem("correct-horse-battery")).isEmpty();
        assertThat(policy.problem("Дуже довга українська фраза як пароль")).isEmpty();
    }

    @Test
    void refusesShortLongAndCommonPasswords() {
        assertThat(policy.problem("short")).isEqualTo("too-short");
        assertThat(policy.problem(null)).isEqualTo("too-short");
        assertThat(policy.problem("a".repeat(73))).isEqualTo("too-long");
        assertThat(policy.problem("ю".repeat(37))).isEqualTo("too-long");
        assertThat(policy.problem("Password123")).isEqualTo("too-common");
    }

    @Test
    void everyAcceptedPasswordCanBeHashed() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

        assertThat(encoder.encode("a".repeat(72))).startsWith("$2a$12$");
        assertThat(encoder.encode("ю".repeat(36))).startsWith("$2a$12$");
    }
}
