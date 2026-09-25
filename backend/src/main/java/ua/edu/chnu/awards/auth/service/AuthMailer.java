package ua.edu.chnu.awards.auth.service;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
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
import ua.edu.chnu.awards.auth.event.NewDeviceSignedIn;
import ua.edu.chnu.awards.auth.event.PasswordResetRequested;
import ua.edu.chnu.awards.auth.event.VerificationRequested;
import ua.edu.chnu.awards.delegation.event.DelegationCreated;
import ua.edu.chnu.awards.delegation.event.DelegationRevoked;
import ua.edu.chnu.awards.user.event.RoleAssigned;
import ua.edu.chnu.awards.user.event.RoleRevoked;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends account emails after the requesting transaction commits; a failed send is retried twice.
 */
@Component
@Slf4j
public class AuthMailer {

    static final int ATTEMPTS = 3;
    private static final long[] PAUSE_MS = {2_000, 5_000};
    private static final String HELLO_UK = "Вітаємо, ";
    private static final String HELLO_EN = "Hello ";
    private static final String EXCLAMATION = "!\n\n";
    private static final String COMMA = ",\n\n";
    private static final String SEPARATOR = "---\n\n";
    private static final String IN_ORGANIZATION = "» у підрозділі «";
    private static final String IN_EN = " in ";

    /** One conversation with the SMTP server at a time; parallel sends make it drop connections. */
    private final Lock smtp = new ReentrantLock();
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
    @TransactionalEventListener
    public void onNewDeviceSignedIn(NewDeviceSignedIn event) {
        deliver(event.email(), "Новий вхід до облікового запису / New sign-in to your account", deviceBody(event));
    }

    @Async
    @TransactionalEventListener
    public void onRoleAssigned(RoleAssigned event) {
        deliver(event.email(), "Роль призначено / Role assigned", roleAssignedBody(event));
    }

    @Async
    @TransactionalEventListener
    public void onRoleRevoked(RoleRevoked event) {
        deliver(event.email(), "Роль відкликано / Role revoked", roleRevokedBody(event));
    }

    @Async
    @TransactionalEventListener
    public void onDelegationCreated(DelegationCreated event) {
        deliver(event.email(), "Делеговано повноваження / Authority delegated", delegationCreatedBody(event));
    }

    @Async
    @TransactionalEventListener
    public void onDelegationRevoked(DelegationRevoked event) {
        event.recipients().forEach(recipient ->
            deliver(recipient, "Делегування відкликано / Delegation revoked", delegationRevokedBody(event)));
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
                smtp.lock();
                try {
                    mailSender.send(message);
                } finally {
                    smtp.unlock();
                }
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
        return HELLO_UK + event.firstName() + EXCLAMATION
            + "Щоб завершити реєстрацію в системі обліку нагород ЧНУ, підтвердьте свою адресу протягом 24 годин:\n"
            + event.link() + "\n\n"
            + "На сторінці підтвердження введіть пароль, який ви обрали під час реєстрації.\n"
            + "Якщо ви не реєструвалися, просто проігноруйте цей лист.\n\n"
            + SEPARATOR
            + HELLO_EN + event.firstName() + COMMA
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

    static String deviceBody(NewDeviceSignedIn event) {
        String facts = "Браузер / Browser: " + event.browser() + "\n"
            + "Система / Operating system: " + event.operatingSystem() + "\n"
            + "IP: " + event.ip() + "\n"
            + "Час / Time: " + event.at() + "\n\n";
        return HELLO_UK + event.firstName() + EXCLAMATION
            + "До вашого облікового запису в системі обліку нагород ЧНУ щойно увійшли з нового пристрою.\n\n"
            + facts
            + "Якщо це були ви, нічого робити не потрібно. Якщо ні, натисніть «Це був не я» протягом 24 годин, "
            + "щоб завершити всі сеанси й задати новий пароль:\n"
            + event.link() + "\n\n"
            + SEPARATOR
            + HELLO_EN + event.firstName() + COMMA
            + "Your ChNU award monitoring account was just signed in from a new device.\n\n"
            + facts
            + "If this was you, nothing needs to be done. If not, use \"This was not me\" within 24 hours to end "
            + "every session and set a new password:\n"
            + event.link() + "\n";
    }

    static String roleAssignedBody(RoleAssigned event) {
        String until = event.validTo() == null ? "" : " до " + event.validTo();
        String untilEn = event.validTo() == null ? "" : " until " + event.validTo();
        return HELLO_UK + event.firstName() + EXCLAMATION
            + event.actor() + " призначив(ла) вам роль «" + event.role() + IN_ORGANIZATION
            + event.organizationUk() + "» з " + event.validFrom() + until + ".\n"
            + "Нові права з'являться після наступного входу до системи.\n\n"
            + SEPARATOR
            + HELLO_EN + event.firstName() + COMMA
            + event.actor() + " granted you the role \"" + event.role() + "\"" + IN_EN + event.organization()
            + " from " + event.validFrom() + untilEn + ".\n"
            + "The new permissions apply from your next sign-in.\n";
    }

    static String roleRevokedBody(RoleRevoked event) {
        return HELLO_UK + event.firstName() + EXCLAMATION
            + event.actor() + " відкликав(ла) вашу роль «" + event.role() + IN_ORGANIZATION
            + event.organizationUk() + "»; останній день дії — " + event.lastDay() + ".\n"
            + "Усі сеанси завершено, тож увійдіть до системи ще раз.\n\n"
            + SEPARATOR
            + HELLO_EN + event.firstName() + COMMA
            + event.actor() + " revoked your role \"" + event.role() + "\"" + IN_EN + event.organization()
            + "; its last day is " + event.lastDay() + ".\n"
            + "Every session was ended, so sign in again.\n";
    }

    static String delegationCreatedBody(DelegationCreated event) {
        String why = event.reason() == null ? "" : "Причина: " + event.reason() + "\n";
        String whyEn = event.reason() == null ? "" : "Reason: " + event.reason() + "\n";
        return HELLO_UK + event.firstName() + EXCLAMATION
            + event.delegator() + " делегував(ла) вам повноваження ролі «" + event.role() + IN_ORGANIZATION
            + event.organizationUk() + "» з " + event.validFrom() + " до " + event.validTo() + ".\n"
            + why
            + "Ви зможете переглядати й погоджувати нагороди цього підрозділу; керування користувачами не "
            + "передається. Повноваження з'являться після наступного входу до системи.\n\n"
            + SEPARATOR
            + HELLO_EN + event.firstName() + COMMA
            + event.delegator() + " delegated the authority of the role \"" + event.role() + "\"" + IN_EN
            + event.organization() + " to you from " + event.validFrom() + " until " + event.validTo() + ".\n"
            + whyEn
            + "You may read and approve the awards of that organisation; user management is not handed over. "
            + "The authority applies from your next sign-in.\n";
    }

    static String delegationRevokedBody(DelegationRevoked event) {
        return "Делегування повноважень ролі «" + event.role() + IN_ORGANIZATION + event.organizationUk()
            + "» для користувача " + event.delegate() + " відкликав(ла) " + event.actor() + ".\n"
            + "Усі сеанси делегата завершено.\n\n"
            + SEPARATOR
            + "The delegation of the role \"" + event.role() + "\"" + IN_EN + event.organization() + " to "
            + event.delegate() + " was revoked by " + event.actor() + ".\n"
            + "Every session of the delegate was ended.\n";
    }

    static String resetBody(PasswordResetRequested event) {
        return HELLO_UK + event.firstName() + EXCLAMATION
            + "Ви попросили скинути пароль у системі обліку нагород ЧНУ. Задайте новий пароль протягом 1 години:\n"
            + event.link() + "\n\n"
            + "Якщо ви не робили цього запиту, проігноруйте цей лист — пароль залишиться незмінним.\n\n"
            + SEPARATOR
            + HELLO_EN + event.firstName() + COMMA
            + "You asked to reset your password for the ChNU award monitoring system. Set a new one within 1 hour:\n"
            + event.link() + "\n\n"
            + "If you did not ask for this, ignore this message; your password stays unchanged.\n";
    }
}
