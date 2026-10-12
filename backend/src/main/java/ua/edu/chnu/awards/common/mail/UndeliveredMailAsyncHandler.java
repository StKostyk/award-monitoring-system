package ua.edu.chnu.awards.common.mail;

import java.lang.reflect.Method;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * Handles exceptions that escape asynchronous methods. An undelivered mail already has its ERROR line from
 * {@link MailDelivery}, so its stack trace goes to DEBUG only; whether it is sent again depends on the event
 * publication registry, which has seen the exception before this handler. Every other exception is logged at
 * ERROR with its stack trace.
 */
@Slf4j
public class UndeliveredMailAsyncHandler implements AsyncUncaughtExceptionHandler {

    private final AsyncUncaughtExceptionHandler fallback = new SimpleAsyncUncaughtExceptionHandler();

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        if (ex instanceof MailNotDeliveredException) {
            log.debug("Async method {} ended without sending its mail", method, ex);
        } else {
            fallback.handleUncaughtException(ex, method, params);
        }
    }
}
