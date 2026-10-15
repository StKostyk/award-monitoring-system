package ua.edu.chnu.awards.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class MailDeliveryTest {

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final List<Long> pauses = new ArrayList<>();
    private final MailDelivery delivery = new MailDelivery(sender, MailRenderers.renderer("chnu"),
        MailRenderers.FROM, pauses::add);

    @BeforeEach
    void mimeMessages() {
        when(sender.createMimeMessage()).thenAnswer(call -> new JavaMailSenderImpl().createMimeMessage());
    }

    @Test
    void sendsFromTheConfiguredAddress() throws Exception {
        delivery.send("olena@chnu.edu.ua", "Нагороду затверджено", "Text");

        MimeMessage message = sent();
        assertThat(addresses(message.getFrom())).containsExactly("awards@chnu.edu.ua");
        assertThat(addresses(message.getRecipients(Message.RecipientType.TO))).containsExactly("olena@chnu.edu.ua");
        assertThat(message.getSubject()).isEqualTo("Нагороду затверджено");
    }

    @Test
    void ac5_1_sendsTheUnchangedTextAndAnHtmlAlternative() throws Exception {
        String text = "Вітаємо!\n\nПереглянути нагороду: http://localhost:4200/awards/1\n";

        delivery.send("olena@chnu.edu.ua", "Subject", MailContent.notification(text));

        MimeMultipart body = (MimeMultipart) sent().getContent();
        assertThat(body.getContentType()).startsWith("multipart/alternative");
        assertThat(body.getCount()).isEqualTo(2);
        BodyPart plain = body.getBodyPart(0);
        assertThat(plain.getContentType()).startsWith("text/plain").containsIgnoringCase("charset=UTF-8");
        assertThat(plain.getContent()).isEqualTo(text);
        BodyPart page = body.getBodyPart(1);
        assertThat(page.getContentType()).startsWith("text/html");
        assertThat((String) page.getContent()).contains(">Переглянути нагороду</a>", "/profile#notifications");
    }

    @Test
    void ac5_3_aPlainTextSendIsASecurityMailWithoutPreferencesLink() throws Exception {
        delivery.send("olena@chnu.edu.ua", "Subject", "Text");

        MimeMultipart body = (MimeMultipart) sent().getContent();
        assertThat((String) body.getBodyPart(1).getContent()).doesNotContain("/profile#notifications");
    }

    @Test
    void addressesOneMessageToEveryRecipient() throws Exception {
        delivery.send(List.of("admin@chnu.edu.ua", "admin2@chnu.edu.ua"), "Subject", "Text");

        assertThat(addresses(sent().getRecipients(Message.RecipientType.TO)))
            .containsExactly("admin@chnu.edu.ua", "admin2@chnu.edu.ua");
    }

    @Test
    void ac1_3_retriesTwiceThenThrowsWithSubjectAndRecipientsOnly() {
        doThrow(new MailSendException("down")).when(sender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> delivery.send("olena@chnu.edu.ua", "Subject", "Secret body"))
            .isInstanceOf(MailNotDeliveredException.class)
            .hasMessageContaining("Subject")
            .hasMessageContaining("olena@chnu.edu.ua")
            .hasMessageNotContaining("Secret body");

        verify(sender, times(MailDelivery.ATTEMPTS)).send(any(MimeMessage.class));
        assertThat(pauses).containsExactly(2_000L, 5_000L);
    }

    @Test
    void stopsRetryingOnceASendSucceeds() {
        doThrow(new MailSendException("down")).doNothing().when(sender).send(any(MimeMessage.class));

        delivery.send("olena@chnu.edu.ua", "Subject", "Text");

        verify(sender, times(2)).send(any(MimeMessage.class));
        assertThat(pauses).containsExactly(2_000L);
    }

    private MimeMessage sent() {
        ArgumentCaptor<MimeMessage> message = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(message.capture());
        return message.getValue();
    }

    private static List<String> addresses(Address[] addresses) {
        return Arrays.stream(addresses).map(Address::toString).toList();
    }
}
