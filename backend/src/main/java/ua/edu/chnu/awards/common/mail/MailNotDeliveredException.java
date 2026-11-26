package ua.edu.chnu.awards.common.mail;

import java.util.List;

/**
 * Thrown when the mail server refused every attempt to send one message; the message names the subject and the
 * recipients only.
 */
public class MailNotDeliveredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception for one undelivered message.
     *
     * @param subject the subject line
     * @param to the recipient addresses
     * @param cause the error of the last attempt
     */
    public MailNotDeliveredException(String subject, List<String> to, Throwable cause) {
        super("Email '" + subject + "' to " + to + " was not delivered", cause);
    }
}
