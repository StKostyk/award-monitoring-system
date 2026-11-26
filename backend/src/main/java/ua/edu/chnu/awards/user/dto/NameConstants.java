package ua.edu.chnu.awards.user.dto;

/**
 * Rules for the names of a person.
 */
public final class NameConstants {

    /** Letters, apostrophes, hyphens and spaces, starting with a letter. */
    public static final String NAME = "^\\p{L}[\\p{L}'’\\- ]*$";

    private NameConstants() {
    }
}
