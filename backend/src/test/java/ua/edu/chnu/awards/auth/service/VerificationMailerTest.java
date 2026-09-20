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

import ua.edu.chnu.awards.auth.event.VerificationRequested;

class VerificationMailerTest {

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final List<Long> pauses = new ArrayList<>();
    private final VerificationMailer mailer = new VerificationMailer(sender, "awards@chnu.edu.ua", pauses::add);
    private final VerificationRequested event = new VerificationRequested("new@chnu.edu.ua", "Олена",
        "http://localhost:4200/verify-email?token=abc");

    @Test
    void ac21_sendsABilingualMessageWithTheLink() {
        mailer.onVerificationRequested(event);

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("new@chnu.edu.ua");
        assertThat(message.getValue().getFrom()).isEqualTo("awards@chnu.edu.ua");
        assertThat(message.getValue().getText()).contains("Олена").contains("token=abc").contains("within 24 hours");
    }

    @Test
    void retriesTwiceThenGivesUp() {
        doThrow(new MailSendException("down")).when(sender).send(any(SimpleMailMessage.class));

        mailer.onVerificationRequested(event);

        verify(sender, times(VerificationMailer.ATTEMPTS)).send(any(SimpleMailMessage.class));
        assertThat(pauses).containsExactly(2_000L, 5_000L);
    }
}
