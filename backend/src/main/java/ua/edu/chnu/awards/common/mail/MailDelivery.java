package ua.edu.chnu.awards.common.mail;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongConsumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends plain-text emails one at a time; a failed send is retried twice before it is logged and dropped.
 */
@Component
@Slf4j
public class MailDelivery {

    static final int ATTEMPTS = 3;
    private static final long[] PAUSE_MS = {2_000, 5_000};

    /** One conversation with the SMTP server at a time; parallel sends make it drop connections. */
    private final Lock smtp = new ReentrantLock();
    private final JavaMailSender mailSender;
    private final String from;
    private final LongConsumer pause;

    /**
     * Creates the delivery with real pauses between attempts.
     *
     * @param mailSender the SMTP client
     * @param from the sender address
     */
    @Autowired
    public MailDelivery(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        this(mailSender, from, MailDelivery::sleep);
    }

    MailDelivery(JavaMailSender mailSender, String from, LongConsumer pause) {
        this.mailSender = mailSender;
        this.from = from;
        this.pause = pause;
    }

    /**
     * Sends one message to a single recipient.
     *
     * @param to the recipient address
     * @param subject the subject line
     * @param text the plain-text body
     * @return true when the mail server accepted the message
     */
    public boolean send(String to, String subject, String text) {
        return send(List.of(to), subject, text);
    }

    /**
     * Sends one message addressed to every recipient.
     *
     * @param to the recipient addresses
     * @param subject the subject line
     * @param text the plain-text body
     * @return true when the mail server accepted the message
     */
    public boolean send(List<String> to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to.toArray(String[]::new));
        message.setSubject(subject);
        message.setText(text);
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                smtp.lock();
                try {
                    mailSender.send(message);
                } finally {
                    smtp.unlock();
                }
                return true;
            } catch (MailException e) {
                log.warn("Email '{}' to {} failed (attempt {} of {}): {}", subject, to, attempt, ATTEMPTS,
                    e.getMessage());
                if (attempt < ATTEMPTS) {
                    pause.accept(PAUSE_MS[attempt - 1]);
                }
            }
        }
        log.error("Email '{}' to {} was not delivered", subject, to);
        return false;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
