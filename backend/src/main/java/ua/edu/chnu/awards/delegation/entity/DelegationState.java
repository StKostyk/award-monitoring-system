package ua.edu.chnu.awards.delegation.entity;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Where a delegation stands today.
 */
public enum DelegationState {

    /** In effect today. */
    ACTIVE,

    /** Its first day has not come yet. */
    UPCOMING,

    /** Its last day has passed. */
    EXPIRED,

    /** Taken back before it ended. */
    REVOKED;

    /**
     * Wire form of the state.
     *
     * @return the name in lower case, as the API spells it
     */
    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
