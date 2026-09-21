package ua.edu.chnu.awards.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import ua.edu.chnu.awards.common.web.ApiProblemException;

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
    void ac21_ac32_requireRefusesWithATypedProblem() {
        assertThatCode(() -> policy.require("correct-horse-battery")).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.require("password123"))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(e.getType()).isEqualTo("password-too-common");
            });
    }

    @Test
    void everyAcceptedPasswordCanBeHashed() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

        assertThat(encoder.encode("a".repeat(72))).startsWith("$2a$12$");
        assertThat(encoder.encode("ю".repeat(36))).startsWith("$2a$12$");
    }
}
