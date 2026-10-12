package ua.edu.chnu.awards.common.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class UndeliveredMailAsyncHandlerTest {

    private final UndeliveredMailAsyncHandler handler = new UndeliveredMailAsyncHandler();

    @Test
    void ac1_undelivered_mail_adds_no_error_line(CapturedOutput output) throws NoSuchMethodException {
        handler.handleUncaughtException(
            new MailNotDeliveredException("Subject", List.of("a@chnu.edu.ua"), new IllegalStateException("down")),
            listenerMethod());

        assertThat(output.getAll()).doesNotContain("ERROR").doesNotContain("MailNotDeliveredException");
    }

    @Test
    void ac2_other_exception_is_logged_with_stack_trace(CapturedOutput output) throws NoSuchMethodException {
        handler.handleUncaughtException(new IllegalStateException("broken listener"), listenerMethod());

        assertThat(output.getAll())
            .contains("ERROR")
            .contains("Unexpected exception occurred invoking async method")
            .contains("java.lang.IllegalStateException: broken listener")
            .contains("\tat ");
    }

    private static Method listenerMethod() throws NoSuchMethodException {
        return Object.class.getMethod("toString");
    }
}
