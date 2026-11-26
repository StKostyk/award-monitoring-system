package ua.edu.chnu.awards.common;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Random secrets for one-time links and unusable passwords.
 */
public final class SecretUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretUtils() {
    }

    /**
     * Random bytes from a cryptographically strong generator, encoded URL-safe Base64 without padding.
     *
     * @param bytes how many random bytes
     * @return the encoded secret
     */
    public static String randomSecret(int bytes) {
        byte[] secret = new byte[bytes];
        RANDOM.nextBytes(secret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }
}
