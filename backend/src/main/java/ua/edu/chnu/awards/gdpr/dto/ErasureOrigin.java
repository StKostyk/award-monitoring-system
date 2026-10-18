package ua.edu.chnu.awards.gdpr.dto;

import java.util.Locale;

/**
 * Who filed an erasure request: the person, or the retention job for an unverified or former account.
 */
public enum ErasureOrigin {

    REQUEST,
    RETENTION;

    /**
     * The origin as written in the audit trail.
     *
     * @return the lower-case name, such as {@code retention}
     */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
