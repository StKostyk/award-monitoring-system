package ua.edu.chnu.awards.auth.service;

import java.util.function.LongConsumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import ua.edu.chnu.awards.auth.event.AccountLocked;
import ua.edu.chnu.awards.auth.event.PasswordResetRequested;
import ua.edu.chnu.awards.auth.event.VerificationRequested;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends account emails after the requesting transaction commits; a failed send is retried twice.
 */
@Component
@Slf4j
public class AuthMailer {

    static final int ATTEMPTS = 3;
    private static final long[] PAUSE_MS = {2_000, 5_000};

    private final JavaMailSender mailSender;
    private final String from;
    private final LongConsumer pause;

    @Autowired
    public AuthMailer(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        this(mailSender, from, AuthMailer::sleep);
    }

    AuthMailer(JavaMailSender mailSender, String from, LongConsumer pause) {
        this.mailSender = mailSender;
        this.from = from;
        this.pause = pause;
    }

    @Async
    @TransactionalEventListener
    public void onVerificationRequested(VerificationRequested event) {
        deliver(event.email(), "Підтвердження адреси / Confirm your address", verificationBody(event));
    }

    @Async
    @TransactionalEventListener
    public void onPasswordResetRequested(PasswordResetRequested event) {
        deliver(event.email(), "Скидання пароля / Password reset", resetBody(event));
    }

    @Async
    @EventListener
    public void onAccountLocked(AccountLocked event) {
        if (event.recipients().isEmpty()) {
            log.warn("Account {} locked but no administrator address to notify", event.email());
            return;
        }
        deliver(event.recipients().toArray(String[]::new), "Обліковий запис заблоковано / Account locked",
            lockedBody(event));
    }

    private void deliver(String to, String subject, String text) {
        deliver(new String[] {to}, subject, text);
    }

    private void deliver(String[] to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                mailSender.send(message);
                return;
            } catch (MailException e) {
                log.warn("Email '{}' to {} failed (attempt {} of {}): {}", subject, to, attempt, ATTEMPTS,
                    e.getMessage());
                if (attempt < ATTEMPTS) {
                    pause.accept(PAUSE_MS[attempt - 1]);
                }
            }
        }
        log.error("Email '{}' to {} was not delivered", subject, to);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static String verificationBody(VerificationRequested event) {
        return "Вітаємо, " + event.firstName() + "!\n\n"
            + "Щоб завершити реєстрацію в системі обліку нагород ЧНУ, підтвердьте свою адресу протягом 24 годин:\n"
            + event.link() + "\n\n"
            + "На сторінці підтвердження введіть пароль, який ви обрали під час реєстрації.\n"
            + "Якщо ви не реєструвалися, просто проігноруйте цей лист.\n\n"
            + "---\n\n"
            + "Hello " + event.firstName() + ",\n\n"
            + "To finish registering with the ChNU award monitoring system, confirm your address within 24 hours:\n"
            + event.link() + "\n\n"
            + "The confirmation page asks for the password you chose when registering.\n"
            + "If you did not register, ignore this message.\n";
    }

    static String lockedBody(AccountLocked event) {
        long minutes = event.lockedFor().toMinutes();
        return "Обліковий запис " + event.email() + " заблоковано на " + minutes
            + " хв після повторних невдалих спроб входу.\n"
            + "Адреса: " + event.ip() + "\nЧас: " + event.at() + "\n\n---\n\n"
            + "Account " + event.email() + " was locked for " + minutes
            + " minutes after repeated failed sign-in attempts.\n"
            + "Address: " + event.ip() + "\nTime: " + event.at() + "\n";
    }

    static String resetBody(PasswordResetRequested event) {
        return "Вітаємо, " + event.firstName() + "!\n\n"
            + "Ви попросили скинути пароль у системі обліку нагород ЧНУ. Задайте новий пароль протягом 1 години:\n"
            + event.link() + "\n\n"
            + "Якщо ви не робили цього запиту, проігноруйте цей лист — пароль залишиться незмінним.\n\n"
            + "---\n\n"
            + "Hello " + event.firstName() + ",\n\n"
            + "You asked to reset your password for the ChNU award monitoring system. Set a new one within 1 hour:\n"
            + event.link() + "\n\n"
            + "If you did not ask for this, ignore this message; your password stays unchanged.\n";
    }
}
