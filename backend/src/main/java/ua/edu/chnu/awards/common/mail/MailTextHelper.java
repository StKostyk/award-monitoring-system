package ua.edu.chnu.awards.common.mail;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Fragments shared by the bilingual email bodies.
 */
public final class MailTextHelper {

    /** Separates the Ukrainian part from the English part. */
    public static final String SEPARATOR = "---\n\n";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private MailTextHelper() {
    }

    /**
     * Formats a moment for a mail.
     *
     * @param at   the moment
     * @param zone the zone of the clock
     * @return the day, month, year, hour and minute in that zone
     */
    public static String dateTime(Instant at, ZoneId zone) {
        return DATE_TIME.withZone(zone).format(at);
    }

    /**
     * Opens the Ukrainian part.
     *
     * @param firstName the recipient's first name
     * @return the greeting line followed by a blank line
     */
    public static String helloUk(String firstName) {
        return "Вітаємо, " + firstName + "!\n\n";
    }

    /**
     * Opens the English part.
     *
     * @param firstName the recipient's first name
     * @return the greeting line followed by a blank line
     */
    public static String helloEn(String firstName) {
        return "Hello " + firstName + ",\n\n";
    }
}
