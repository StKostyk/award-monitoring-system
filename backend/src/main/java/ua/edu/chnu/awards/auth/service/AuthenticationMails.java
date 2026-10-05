package ua.edu.chnu.awards.auth.service;

import static ua.edu.chnu.awards.common.mail.MailTextHelper.SEPARATOR;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloEn;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloUk;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import ua.edu.chnu.awards.auth.event.AccountLocked;
import ua.edu.chnu.awards.auth.event.EmailChangeRequested;
import ua.edu.chnu.awards.auth.event.EmailChanged;
import ua.edu.chnu.awards.auth.event.EmailRestored;
import ua.edu.chnu.awards.auth.event.NewDeviceSignedIn;
import ua.edu.chnu.awards.auth.event.PasswordResetRequested;
import ua.edu.chnu.awards.auth.event.VerificationRequested;
import ua.edu.chnu.awards.common.mail.MailDelivery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends the account emails (verification, password reset, address change, new device, lockout) after the requesting
 * transaction commits.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticationMails {

    private final MailDelivery delivery;

    /**
     * Sends the address confirmation link.
     *
     * @param event the committed registration or resend
     */
    @Async
    @TransactionalEventListener
    public void onVerificationRequested(VerificationRequested event) {
        delivery.send(event.email(), "Підтвердження адреси / Confirm your address", verificationBody(event));
    }

    /**
     * Sends the password reset link.
     *
     * @param event the committed reset request
     */
    @Async
    @TransactionalEventListener
    public void onPasswordResetRequested(PasswordResetRequested event) {
        delivery.send(event.email(), "Скидання пароля / Password reset", resetBody(event));
    }

    /**
     * Sends the confirmation link of a sign-in address change to the new address and a warning to the current one.
     *
     * @param event the committed change request
     */
    @Async
    @TransactionalEventListener
    public void onEmailChangeRequested(EmailChangeRequested event) {
        delivery.send(event.email(), "Підтвердження нової адреси / Confirm your new address",
            emailChangeBody(event));
        delivery.send(event.currentEmail(), "Запит на зміну адреси для входу / Sign-in address change requested",
            emailChangeWarningBody(event));
    }

    /**
     * Tells the previous address that the account now signs in with another one.
     *
     * @param event the committed change
     */
    @Async
    @TransactionalEventListener
    public void onEmailChanged(EmailChanged event) {
        delivery.send(event.oldEmail(), "Адресу для входу змінено / Your sign-in address was changed",
            emailChangedBody(event));
    }

    /**
     * Tells the address an account left that a "this was not me" link moved the account back.
     *
     * @param event the committed restore
     */
    @Async
    @TransactionalEventListener
    public void onEmailRestored(EmailRestored event) {
        delivery.send(event.replacedEmail(), "Адресу для входу повернуто / Sign-in address moved back",
            emailRestoredBody(event));
    }

    /**
     * Announces a sign-in from an unknown device with the "not me" link.
     *
     * @param event the committed device record
     */
    @Async
    @TransactionalEventListener
    public void onNewDeviceSignedIn(NewDeviceSignedIn event) {
        delivery.send(event.email(), "Новий вхід до облікового запису / New sign-in to your account",
            deviceBody(event));
    }

    /**
     * Tells every system administrator that an account was locked.
     *
     * @param event the lockout
     */
    @Async
    @EventListener
    public void onAccountLocked(AccountLocked event) {
        if (event.recipients().isEmpty()) {
            log.warn("Account {} locked but no administrator address to notify", event.email());
            return;
        }
        delivery.send(event.recipients(), "Обліковий запис заблоковано / Account locked", lockedBody(event));
    }

    static String verificationBody(VerificationRequested event) {
        return helloUk(event.firstName())
            + "Щоб завершити реєстрацію в системі обліку нагород ЧНУ, підтвердьте свою адресу протягом 24 годин:\n"
            + event.link() + "\n\n"
            + "На сторінці підтвердження введіть пароль, який ви обрали під час реєстрації.\n"
            + "Якщо ви не реєструвалися, просто проігноруйте цей лист.\n\n"
            + SEPARATOR
            + helloEn(event.firstName())
            + "To finish registering with the ChNU award monitoring system, confirm your address within 24 hours:\n"
            + event.link() + "\n\n"
            + "The confirmation page asks for the password you chose when registering.\n"
            + "If you did not register, ignore this message.\n";
    }

    static String resetBody(PasswordResetRequested event) {
        return helloUk(event.firstName())
            + "Ви попросили скинути пароль у системі обліку нагород ЧНУ. Задайте новий пароль протягом 1 години:\n"
            + event.link() + "\n\n"
            + "Якщо ви не робили цього запиту, проігноруйте цей лист — пароль залишиться незмінним.\n\n"
            + SEPARATOR
            + helloEn(event.firstName())
            + "You asked to reset your password for the ChNU award monitoring system. Set a new one within 1 hour:\n"
            + event.link() + "\n\n"
            + "If you did not ask for this, ignore this message; your password stays unchanged.\n";
    }

    static String emailChangeBody(EmailChangeRequested event) {
        return helloUk(event.firstName())
            + "Ви попросили входити до системи обліку нагород ЧНУ з адресою " + event.email()
            + ". Підтвердьте її протягом 1 години:\n"
            + event.link() + "\n\n"
            + "Після підтвердження всі сеанси буде завершено, і ви ввійдете вже з новою адресою.\n"
            + "Якщо ви не робили цього запиту, проігноруйте цей лист — адреса залишиться незмінною.\n\n"
            + SEPARATOR
            + helloEn(event.firstName())
            + "You asked to sign in to the ChNU award monitoring system with " + event.email()
            + ". Confirm it within 1 hour:\n"
            + event.link() + "\n\n"
            + "Confirming ends every session; you then sign in with the new address.\n"
            + "If you did not ask for this, ignore this message; your address stays unchanged.\n";
    }

    static String emailChangeWarningBody(EmailChangeRequested event) {
        return helloUk(event.firstName())
            + "Для вашого облікового запису в системі обліку нагород ЧНУ щойно попросили змінити адресу для входу на "
            + event.email() + ". Посилання для підтвердження надіслано на ту адресу й діє 1 годину.\n\n"
            + "Якщо це були не ви, негайно змініть пароль — це скасує зміну адреси й завершить усі сеанси:\n"
            + event.resetLink() + "\n\n"
            + SEPARATOR
            + helloEn(event.firstName())
            + "Somebody just asked to change the sign-in address of your ChNU award monitoring account to "
            + event.email() + ". The confirmation link went to that address and works for 1 hour.\n\n"
            + "If this was not you, reset your password at once; that cancels the change and ends every session:\n"
            + event.resetLink() + "\n";
    }

    static String emailChangedBody(EmailChanged event) {
        return helloUk(event.firstName())
            + "Адресу для входу до вашого облікового запису в системі обліку нагород ЧНУ змінено на "
            + event.newEmail() + ". Цю адресу більше не використовують для входу.\n\n"
            + "Якщо це були не ви, натисніть «Це був не я» протягом 24 годин: обліковий запис повернеться на цю "
            + "адресу, усі сеанси буде завершено, а посилання для нового пароля надійде сюди:\n"
            + event.revokeLink() + "\n\n"
            + SEPARATOR
            + helloEn(event.firstName())
            + "The sign-in address of your ChNU award monitoring account was changed to " + event.newEmail()
            + ". This address is no longer used to sign in.\n\n"
            + "If this was not you, use \"This was not me\" within 24 hours: the account moves back to this "
            + "address, every session ends and a link for a new password comes here:\n"
            + event.revokeLink() + "\n";
    }

    static String emailRestoredBody(EmailRestored event) {
        return helloUk(event.firstName())
            + "Власник облікового запису в системі обліку нагород ЧНУ натиснув «Це був не я» в листі на попередню "
            + "адресу, тож для входу знову використовують " + event.restoredEmail()
            + ". Цю адресу більше не використовують для входу; усі сеанси завершено.\n"
            + "Якщо адресу змінювали ви, зверніться до адміністратора системи.\n\n"
            + SEPARATOR
            + helloEn(event.firstName())
            + "The owner of a ChNU award monitoring account used \"This was not me\" in a message to the previous "
            + "address, so the account signs in with " + event.restoredEmail()
            + " again. This address is no longer used to sign in; every session has ended.\n"
            + "If you changed the address yourself, contact the system administrator.\n";
    }

    static String deviceBody(NewDeviceSignedIn event) {
        String facts = "Браузер / Browser: " + event.browser() + "\n"
            + "Система / Operating system: " + event.operatingSystem() + "\n"
            + "IP: " + event.ip() + "\n"
            + "Час / Time: " + event.at() + "\n\n";
        return helloUk(event.firstName())
            + "До вашого облікового запису в системі обліку нагород ЧНУ щойно увійшли з нового пристрою.\n\n"
            + facts
            + "Якщо це були ви, нічого робити не потрібно. Якщо ні, натисніть «Це був не я» протягом 24 годин, "
            + "щоб завершити всі сеанси й задати новий пароль:\n"
            + event.link() + "\n\n"
            + SEPARATOR
            + helloEn(event.firstName())
            + "Your ChNU award monitoring account was just signed in from a new device.\n\n"
            + facts
            + "If this was you, nothing needs to be done. If not, use \"This was not me\" within 24 hours to end "
            + "every session and set a new password:\n"
            + event.link() + "\n";
    }

    static String lockedBody(AccountLocked event) {
        long minutes = event.lockedFor().toMinutes();
        return "Обліковий запис " + event.email() + " заблоковано на " + minutes
            + " хв після повторних невдалих спроб входу.\n"
            + "Адреса: " + event.ip() + "\nЧас: " + event.at() + "\n\n" + SEPARATOR
            + "Account " + event.email() + " was locked for " + minutes
            + " minutes after repeated failed sign-in attempts.\n"
            + "Address: " + event.ip() + "\nTime: " + event.at() + "\n";
    }
}
