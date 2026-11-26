package ua.edu.chnu.awards.support;

import java.util.Arrays;
import java.util.List;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Reads sent MIME messages in tests.
 */
public final class TestMails {

    private TestMails() {
    }

    /**
     * The To addresses of a message.
     *
     * @param message the message
     * @return the addresses, empty when the message has none or cannot be read
     */
    public static List<String> recipients(MimeMessage message) {
        try {
            Address[] to = message.getRecipients(Message.RecipientType.TO);
            return to == null ? List.of() : Arrays.stream(to).map(Address::toString).toList();
        } catch (MessagingException e) {
            return List.of();
        }
    }

    /**
     * The subject of a message.
     *
     * @param message the message
     * @return the decoded subject, or null when it cannot be read
     */
    public static String subject(MimeMessage message) {
        try {
            return message.getSubject();
        } catch (MessagingException e) {
            return null;
        }
    }
}
