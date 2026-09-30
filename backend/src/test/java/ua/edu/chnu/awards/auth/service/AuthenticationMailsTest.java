package ua.edu.chnu.awards.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ua.edu.chnu.awards.auth.event.AccountLocked;
import ua.edu.chnu.awards.auth.event.EmailChangeRequested;
import ua.edu.chnu.awards.auth.event.EmailChanged;
import ua.edu.chnu.awards.auth.event.NewDeviceSignedIn;
import ua.edu.chnu.awards.auth.event.PasswordResetRequested;
import ua.edu.chnu.awards.auth.event.VerificationRequested;
import ua.edu.chnu.awards.common.mail.MailDelivery;

class AuthenticationMailsTest {

    private final MailDelivery delivery = mock(MailDelivery.class);
    private final AuthenticationMails mails = new AuthenticationMails(delivery);
    private final ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
    private final ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);

    @Test
    void ac21_sendsABilingualVerificationMessageWithTheLink() {
        mails.onVerificationRequested(new VerificationRequested("new@chnu.edu.ua", "Олена",
            "http://localhost:4200/verify-email?token=abc"));

        verify(delivery).send(eq("new@chnu.edu.ua"), subject.capture(), text.capture());
        assertThat(subject.getValue()).contains("Підтвердження адреси").contains("Confirm your address");
        assertThat(text.getValue()).contains("Олена").contains("token=abc").contains("within 24 hours")
            .contains("password you chose");
    }

    @Test
    void ac31_sendsABilingualResetMessageWithTheLink() {
        mails.onPasswordResetRequested(new PasswordResetRequested("olena@chnu.edu.ua", "Олена",
            "http://localhost:4200/reset-password?token=xyz"));

        verify(delivery).send(eq("olena@chnu.edu.ua"), subject.capture(), text.capture());
        assertThat(subject.getValue()).contains("Скидання пароля").contains("Password reset");
        assertThat(text.getValue()).contains("Олена").contains("token=xyz").contains("within 1 hour")
            .doesNotContain("24 hours");
    }

    @Test
    void ac14_sendsTheLinkToTheNewAddressAndAWarningToTheCurrentOne() {
        mails.onEmailChangeRequested(new EmailChangeRequested("mover.new@chnu.edu.ua", "mover@chnu.edu.ua",
            "Петро", "http://localhost:4200/confirm-email-change?token=chg", "http://localhost:4200/forgot-password"));

        verify(delivery).send(eq("mover.new@chnu.edu.ua"), subject.capture(), text.capture());
        assertThat(subject.getValue()).contains("Підтвердження нової адреси").contains("Confirm your new address");
        assertThat(text.getValue()).contains("Петро").contains("token=chg").contains("within 1 hour")
            .contains("mover.new@chnu.edu.ua");
        verify(delivery).send(eq("mover@chnu.edu.ua"), subject.capture(), text.capture());
        assertThat(subject.getValue()).contains("Sign-in address change requested");
        assertThat(text.getValue()).contains("mover.new@chnu.edu.ua").contains("/forgot-password")
            .doesNotContain("token=chg");
    }

    @Test
    void ac15_tellsTheOldAddressWhereTheAccountWent() {
        mails.onEmailChanged(new EmailChanged("mover@chnu.edu.ua", "mover.new@chnu.edu.ua", "Петро"));

        verify(delivery).send(eq("mover@chnu.edu.ua"), subject.capture(), text.capture());
        assertThat(subject.getValue()).contains("Адресу для входу змінено").contains("sign-in address was changed");
        assertThat(text.getValue()).contains("mover.new@chnu.edu.ua").contains("адміністратор")
            .contains("administrator");
    }

    @Test
    void ac51_ac53_announcesANewDeviceWithItsFactsAndTheNotMeLink() {
        mails.onNewDeviceSignedIn(new NewDeviceSignedIn("olena@chnu.edu.ua", "Олена", "Chrome", "Windows",
            "203.0.113.7", Instant.parse("2026-09-21T10:00:00Z"),
            "http://localhost:4200/security/not-me?token=dev"));

        verify(delivery).send(eq("olena@chnu.edu.ua"), subject.capture(), text.capture());
        assertThat(subject.getValue()).contains("Новий вхід").contains("New sign-in");
        assertThat(text.getValue()).contains("Олена").contains("Chrome").contains("Windows")
            .contains("203.0.113.7").contains("2026-09-21T10:00:00Z").contains("token=dev").contains("24 hours");
    }

    @Test
    void ac44_tellsEveryAdministratorAboutALockedAccount() {
        mails.onAccountLocked(new AccountLocked("dean@chnu.edu.ua", "203.0.113.7",
            Instant.parse("2026-09-21T10:00:00Z"), Duration.ofMinutes(45),
            List.of("admin@chnu.edu.ua", "admin2@chnu.edu.ua")));

        verify(delivery).send(eq(List.of("admin@chnu.edu.ua", "admin2@chnu.edu.ua")), subject.capture(),
            text.capture());
        assertThat(subject.getValue()).contains("Account locked");
        assertThat(text.getValue()).contains("dean@chnu.edu.ua").contains("203.0.113.7")
            .contains("2026-09-21T10:00:00Z").contains("45 minutes");
    }

    @Test
    void ac44_noAdministratorMeansNoMessage() {
        mails.onAccountLocked(new AccountLocked("dean@chnu.edu.ua", "203.0.113.7", Instant.now(),
            Duration.ofMinutes(30), List.of()));

        verify(delivery, never()).send(anyList(), anyString(), anyString());
    }
}
