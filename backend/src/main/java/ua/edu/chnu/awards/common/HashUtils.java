package ua.edu.chnu.awards.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Digests used for values kept at rest or as cache keys.
 */
public final class HashUtils {

    private HashUtils() {
    }

    /**
     * SHA-256 of the UTF-8 bytes as lower-case hex.
     *
     * @param value the text
     * @return 64 hex characters
     */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
