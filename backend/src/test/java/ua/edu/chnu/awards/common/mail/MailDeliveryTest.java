package ua.edu.chnu.awards.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

class MailDeliveryTest {

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final List<Long> pauses = new ArrayList<>();
    private final MailDelivery delivery = new MailDelivery(sender, "awards@chnu.edu.ua", pauses::add);

    @Test
    void sendsFromTheConfiguredAddress() {
        delivery.send("olena@chnu.edu.ua", "Subject", "Text");

        SimpleMailMessage message = sent();
        assertThat(message.getFrom()).isEqualTo("awards@chnu.edu.ua");
        assertThat(message.getTo()).containsExactly("olena@chnu.edu.ua");
        assertThat(message.getSubject()).isEqualTo("Subject");
        assertThat(message.getText()).isEqualTo("Text");
    }

    @Test
    void addressesOneMessageToEveryRecipient() {
        delivery.send(List.of("admin@chnu.edu.ua", "admin2@chnu.edu.ua"), "Subject", "Text");

        assertThat(sent().getTo()).containsExactly("admin@chnu.edu.ua", "admin2@chnu.edu.ua");
    }

    @Test
    void ac1_3_retriesTwiceThenThrowsWithSubjectAndRecipientsOnly() {
        doThrow(new MailSendException("down")).when(sender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> delivery.send("olena@chnu.edu.ua", "Subject", "Secret body"))
            .isInstanceOf(MailNotDeliveredException.class)
            .hasMessageContaining("Subject")
            .hasMessageContaining("olena@chnu.edu.ua")
            .hasMessageNotContaining("Secret body");

        verify(sender, times(MailDelivery.ATTEMPTS)).send(any(SimpleMailMessage.class));
        assertThat(pauses).containsExactly(2_000L, 5_000L);
    }

    @Test
    void stopsRetryingOnceASendSucceeds() {
        doThrow(new MailSendException("down")).doNothing().when(sender).send(any(SimpleMailMessage.class));

        delivery.send("olena@chnu.edu.ua", "Subject", "Text");

        verify(sender, times(2)).send(any(SimpleMailMessage.class));
        assertThat(pauses).containsExactly(2_000L);
    }

    private SimpleMailMessage sent() {
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        return message.getValue();
    }
}
