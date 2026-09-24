package ua.edu.chnu.awards.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import ua.edu.chnu.awards.auth.event.AccountLocked;
import ua.edu.chnu.awards.auth.event.NewDeviceSignedIn;
import ua.edu.chnu.awards.auth.event.PasswordResetRequested;
import ua.edu.chnu.awards.auth.event.VerificationRequested;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.event.RoleAssigned;
import ua.edu.chnu.awards.user.event.RoleRevoked;

class AuthMailerTest {

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final List<Long> pauses = new ArrayList<>();
    private final AuthMailer mailer = new AuthMailer(sender, "awards@chnu.edu.ua", pauses::add);
    private final VerificationRequested verification = new VerificationRequested("new@chnu.edu.ua", "Олена",
        "http://localhost:4200/verify-email?token=abc");

    @Test
    void ac21_sendsABilingualVerificationMessageWithTheLink() {
        mailer.onVerificationRequested(verification);

        SimpleMailMessage message = sent();
        assertThat(message.getTo()).containsExactly("new@chnu.edu.ua");
        assertThat(message.getFrom()).isEqualTo("awards@chnu.edu.ua");
        assertThat(message.getText()).contains("Олена").contains("token=abc").contains("within 24 hours")
            .contains("password you chose");
    }

    @Test
    void ac31_sendsABilingualResetMessageWithTheLink() {
        mailer.onPasswordResetRequested(new PasswordResetRequested("olena@chnu.edu.ua", "Олена",
            "http://localhost:4200/reset-password?token=xyz"));

        SimpleMailMessage message = sent();
        assertThat(message.getTo()).containsExactly("olena@chnu.edu.ua");
        assertThat(message.getSubject()).contains("Скидання пароля").contains("Password reset");
        assertThat(message.getText()).contains("Олена").contains("token=xyz").contains("within 1 hour")
            .doesNotContain("24 hours");
    }

    @Test
    void ac51_ac53_announcesANewDeviceWithItsFactsAndTheNotMeLink() {
        mailer.onNewDeviceSignedIn(new NewDeviceSignedIn("olena@chnu.edu.ua", "Олена", "Chrome", "Windows",
            "203.0.113.7", Instant.parse("2026-09-21T10:00:00Z"),
            "http://localhost:4200/security/not-me?token=dev"));

        SimpleMailMessage message = sent();
        assertThat(message.getTo()).containsExactly("olena@chnu.edu.ua");
        assertThat(message.getSubject()).contains("Новий вхід").contains("New sign-in");
        assertThat(message.getText()).contains("Олена").contains("Chrome").contains("Windows")
            .contains("203.0.113.7").contains("2026-09-21T10:00:00Z").contains("token=dev").contains("24 hours");
    }

    @Test
    void ac2_5_tellsTheHolderWhichRoleWasGrantedWhereAndUntilWhen() {
        mailer.onRoleAssigned(new RoleAssigned("member@chnu.edu.ua", "Анастасія", RoleType.FACULTY_SECRETARY,
            "Faculty of Mathematics and Informatics", "Факультет математики та інформатики", "Марія Мартинюк",
            LocalDate.of(2026, 9, 22), LocalDate.of(2026, 10, 22)));

        SimpleMailMessage message = sent();
        assertThat(message.getTo()).containsExactly("member@chnu.edu.ua");
        assertThat(message.getSubject()).isEqualTo("Роль призначено / Role assigned");
        assertThat(message.getText()).contains("FACULTY_SECRETARY", "Факультет математики та інформатики",
            "Faculty of Mathematics and Informatics", "Марія Мартинюк", "2026-09-22", "2026-10-22");
    }

    @Test
    void ac2_5_tellsTheFormerHolderTheLastDayAndThatEverySessionEnded() {
        mailer.onRoleRevoked(new RoleRevoked("member@chnu.edu.ua", "Анастасія", RoleType.DEAN,
            "Faculty of Mathematics and Informatics", "Факультет математики та інформатики", "Марія Мартинюк",
            LocalDate.of(2026, 9, 21)));

        SimpleMailMessage message = sent();
        assertThat(message.getSubject()).isEqualTo("Роль відкликано / Role revoked");
        assertThat(message.getText()).contains("DEAN", "2026-09-21", "Every session was ended");
    }

    @Test
    void ac44_tellsEveryAdministratorAboutALockedAccount() {
        mailer.onAccountLocked(new AccountLocked("dean@chnu.edu.ua", "203.0.113.7",
            Instant.parse("2026-09-21T10:00:00Z"), Duration.ofMinutes(45),
            List.of("admin@chnu.edu.ua", "admin2@chnu.edu.ua")));

        SimpleMailMessage message = sent();
        assertThat(message.getTo()).containsExactly("admin@chnu.edu.ua", "admin2@chnu.edu.ua");
        assertThat(message.getText()).contains("dean@chnu.edu.ua").contains("203.0.113.7")
            .contains("2026-09-21T10:00:00Z").contains("45 minutes");
    }

    @Test
    void ac44_noAdministratorMeansNoMessage() {
        mailer.onAccountLocked(new AccountLocked("dean@chnu.edu.ua", "203.0.113.7", Instant.now(),
            Duration.ofMinutes(30), List.of()));

        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void retriesTwiceThenGivesUp() {
        doThrow(new MailSendException("down")).when(sender).send(any(SimpleMailMessage.class));

        mailer.onVerificationRequested(verification);

        verify(sender, times(AuthMailer.ATTEMPTS)).send(any(SimpleMailMessage.class));
        assertThat(pauses).containsExactly(2_000L, 5_000L);
    }

    private SimpleMailMessage sent() {
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        return message.getValue();
    }
}
