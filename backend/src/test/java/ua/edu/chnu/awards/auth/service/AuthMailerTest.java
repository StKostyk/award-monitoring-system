package ua.edu.chnu.awards.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import ua.edu.chnu.awards.auth.event.PasswordResetRequested;
import ua.edu.chnu.awards.auth.event.VerificationRequested;

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
