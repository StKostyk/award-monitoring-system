package ua.edu.chnu.awards.common.mail;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongConsumer;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends emails one at a time as {@code multipart/alternative}: the plain text and its HTML rendering in the brand
 * layout. A failed send is retried twice before it is logged and thrown, so that the event publication of the
 * calling listener stays incomplete.
 */
@Component
@Slf4j
public class MailDelivery {

    static final int ATTEMPTS = 3;
    private static final long[] PAUSE_MS = {2_000, 5_000};
    private static final String UTF_8 = StandardCharsets.UTF_8.name();

    /** One conversation with the SMTP server at a time; parallel sends make it drop connections. */
    private final Lock smtp = new ReentrantLock();
    private final JavaMailSender mailSender;
    private final MailHtmlRenderer html;
    private final String from;
    private final LongConsumer pause;

    /**
     * Creates the delivery with real pauses between attempts.
     *
     * @param mailSender the SMTP client
     * @param html the renderer of the HTML part
     * @param from the sender address
     */
    @Autowired
    public MailDelivery(JavaMailSender mailSender, MailHtmlRenderer html, @Value("${app.mail.from}") String from) {
        this(mailSender, html, from, MailDelivery::sleep);
    }

    MailDelivery(JavaMailSender mailSender, MailHtmlRenderer html, String from, LongConsumer pause) {
        this.mailSender = mailSender;
        this.html = html;
        this.from = from;
        this.pause = pause;
    }

    /**
     * Sends a security or privacy message, without the preferences link, to a single recipient.
     *
     * @param to the recipient address
     * @param subject the subject line
     * @param text the plain-text body
     * @throws MailNotDeliveredException when the mail server refused every attempt
     */
    public void send(String to, String subject, String text) {
        send(List.of(to), subject, MailContent.mandatory(text));
    }

    /**
     * Sends one message to a single recipient.
     *
     * @param to the recipient address
     * @param subject the subject line
     * @param content the plain-text body and whether the footer links to the preferences
     * @throws MailNotDeliveredException when the mail server refused every attempt
     */
    public void send(String to, String subject, MailContent content) {
        send(List.of(to), subject, content);
    }

    /**
     * Sends a security or privacy message, without the preferences link, addressed to every recipient.
     *
     * @param to the recipient addresses
     * @param subject the subject line
     * @param text the plain-text body
     * @throws MailNotDeliveredException when the mail server refused every attempt
     */
    public void send(List<String> to, String subject, String text) {
        send(to, subject, MailContent.mandatory(text));
    }

    /**
     * Sends one message addressed to every recipient.
     *
     * @param to the recipient addresses
     * @param subject the subject line
     * @param content the plain-text body and whether the footer links to the preferences
     * @throws MailNotDeliveredException when the mail server refused every attempt
     */
    public void send(List<String> to, String subject, MailContent content) {
        MimeMessage message = message(to, subject, content);
        MailException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                smtp.lock();
                try {
                    mailSender.send(message);
                } finally {
                    smtp.unlock();
                }
                return;
            } catch (MailException e) {
                last = e;
                log.warn("Email '{}' to {} failed (attempt {} of {}): {}", subject, to, attempt, ATTEMPTS,
                    e.getMessage());
                if (attempt < ATTEMPTS) {
                    pause.accept(PAUSE_MS[attempt - 1]);
                }
            }
        }
        log.error("Email '{}' to {} was not delivered", subject, to);
        throw new MailNotDeliveredException(subject, to, last);
    }

    private MimeMessage message(List<String> to, String subject, MailContent content) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper headers = new MimeMessageHelper(message, UTF_8);
            headers.setFrom(from);
            headers.setTo(to.toArray(String[]::new));
            headers.setSubject(subject);
            MimeBodyPart text = new MimeBodyPart();
            text.setText(content.text(), UTF_8);
            MimeBodyPart page = new MimeBodyPart();
            page.setContent(html.render(subject, content), "text/html; charset=UTF-8");
            message.setContent(new MimeMultipart("alternative", text, page));
            message.saveChanges();
        } catch (MessagingException e) {
            throw new MailPreparationException(e);
        }
        return message;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
