package ua.edu.chnu.awards.auth.service;

import java.util.function.LongConsumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import ua.edu.chnu.awards.auth.event.VerificationRequested;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends verification emails after the registration transaction commits; a failed send is retried twice.
 */
@Component
@Slf4j
public class VerificationMailer {

    static final int ATTEMPTS = 3;
    private static final long[] PAUSE_MS = {2_000, 5_000};

    private final JavaMailSender mailSender;
    private final String from;
    private final LongConsumer pause;

    @Autowired
    public VerificationMailer(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        this(mailSender, from, VerificationMailer::sleep);
    }

    VerificationMailer(JavaMailSender mailSender, String from, LongConsumer pause) {
        this.mailSender = mailSender;
        this.from = from;
        this.pause = pause;
    }

    @Async
    @TransactionalEventListener
    public void onVerificationRequested(VerificationRequested event) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(event.email());
        message.setSubject("Підтвердження адреси / Confirm your address");
        message.setText(body(event));
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                mailSender.send(message);
                return;
            } catch (MailException e) {
                log.warn("Verification email to {} failed (attempt {} of {}): {}", event.email(), attempt, ATTEMPTS,
                    e.getMessage());
                if (attempt < ATTEMPTS) {
                    pause.accept(PAUSE_MS[attempt - 1]);
                }
            }
        }
        log.error("Verification email to {} was not delivered", event.email());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static String body(VerificationRequested event) {
        return "Вітаємо, " + event.firstName() + "!\n\n"
            + "Щоб завершити реєстрацію в системі обліку нагород ЧНУ, підтвердьте свою адресу протягом 24 годин:\n"
            + event.link() + "\n\n"
            + "Якщо ви не реєструвалися, просто проігноруйте цей лист.\n\n"
            + "---\n\n"
            + "Hello " + event.firstName() + ",\n\n"
            + "To finish registering with the ChNU award monitoring system, confirm your address within 24 hours:\n"
            + event.link() + "\n\n"
            + "If you did not register, ignore this message.\n";
    }
}
