package ua.edu.chnu.awards.auth.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Password rule: at least 10 characters, at most 72 bytes of UTF-8 (the limit of BCrypt) and not on the list of
 * well-known passwords.
 */
@Component
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_BYTES = 72;

    private final Set<String> commonPasswords;

    public PasswordPolicy() {
        try {
            commonPasswords = new ClassPathResource("passwords/common-passwords.txt")
                .getContentAsString(StandardCharsets.UTF_8).lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(line -> line.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Reason the password is refused, or empty when it is acceptable.
     *
     * @param password candidate
     * @return {@code too-short}, {@code too-long}, {@code too-common} or empty
     */
    public String problem(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "too-short";
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return "too-long";
        }
        if (commonPasswords.contains(password.toLowerCase(Locale.ROOT))) {
            return "too-common";
        }
        return "";
    }
}
