package ua.edu.chnu.awards.common;

import java.util.Locale;
import java.util.Optional;

/**
 * Canonical form of a typed email address for keys, lookups and comparisons.
 */
public final class EmailUtils {

    private static final int MAX_LENGTH = 254;

    private EmailUtils() {
    }

    /**
     * Trims, lower-cases, strips control characters and caps the value at the length of a valid address.
     *
     * @param email the typed value, may be null
     * @return the normalised value, empty for null
     */
    public static String normalize(String email) {
        String value = Optional.ofNullable(email).orElse("").replaceAll("\\p{Cntrl}", "").trim()
            .toLowerCase(Locale.ROOT);
        return value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
    }
}
