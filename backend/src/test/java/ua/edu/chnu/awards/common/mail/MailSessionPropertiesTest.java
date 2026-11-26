package ua.edu.chnu.awards.common.mail;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class MailSessionPropertiesTest {

    private static final String DEFAULT_FROM = "awards@chnu.edu.ua";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class));

    @Test
    void ac1_message_id_host_comes_from_the_sender_address() {
        runner.run(context -> {
            Session session = context.getBean(JavaMailSenderImpl.class).getSession();
            MimeMessage message = new MimeMessage(session);
            message.saveChanges();

            assertThat(session.getProperty("mail.from")).isEqualTo(DEFAULT_FROM);
            assertThat(message.getMessageID()).endsWith("@chnu.edu.ua>");
        });
    }

    @Test
    void ac2_ehlo_host_defaults_to_the_university_domain() {
        runner.run(context -> {
            Session session = context.getBean(JavaMailSenderImpl.class).getSession();

            assertThat(session.getProperty("mail.smtp.localhost")).isEqualTo("chnu.edu.ua");
            assertThat(session.getProperty("mail.smtps.localhost")).isEqualTo("chnu.edu.ua");
        });
    }

    @Test
    void ac2_ehlo_host_is_configurable() {
        runner.withPropertyValues("APP_MAIL_EHLO_HOST=awards.example.org")
            .run(context -> {
                Session session = context.getBean(JavaMailSenderImpl.class).getSession();

                assertThat(session.getProperty("mail.smtp.localhost")).isEqualTo("awards.example.org");
                assertThat(session.getProperty("mail.smtps.localhost")).isEqualTo("awards.example.org");
            });
    }

    @Test
    void ac1_sender_follows_the_configured_from_address() {
        runner.withPropertyValues("APP_MAIL_FROM=noreply@example.org")
            .run(context -> assertThat(context.getBean(JavaMailSenderImpl.class).getSession()
                .getProperty("mail.from")).isEqualTo("noreply@example.org"));
    }
}
