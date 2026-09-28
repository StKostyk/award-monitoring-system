package ua.edu.chnu.awards.common.mail;

/**
 * Fragments shared by the bilingual email bodies.
 */
public final class MailTextHelper {

    /** Separates the Ukrainian part from the English part. */
    public static final String SEPARATOR = "---\n\n";

    private MailTextHelper() {
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
