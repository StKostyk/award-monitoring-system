package ua.edu.chnu.awards.common.mail;

/**
 * The plain-text body of a mail and whether its footer links to the notification preferences.
 *
 * @param text            the plain-text body, also the source of the HTML part
 * @param preferencesLink whether the recipient can switch this kind of mail off
 */
public record MailContent(String text, boolean preferencesLink) {

    /**
     * A mail of a notification type the recipient can switch off.
     *
     * @param text the plain-text body
     * @return the content with the preferences link
     */
    public static MailContent notification(String text) {
        return new MailContent(text, true);
    }

    /**
     * A security or privacy mail that is always sent.
     *
     * @param text the plain-text body
     * @return the content without the preferences link
     */
    public static MailContent mandatory(String text) {
        return new MailContent(text, false);
    }
}
