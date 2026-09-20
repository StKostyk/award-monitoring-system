package ua.edu.chnu.awards.auth.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;

/**
 * After an expired session has been invalidated, sends the browser back to the same URL so the request is
 * handled anew as anonymous: an authorization request then lands on the login page with its parameters kept.
 */
public class RetryRequestSessionExpiredStrategy implements SessionInformationExpiredStrategy {

    @Override
    public void onExpiredSessionDetected(SessionInformationExpiredEvent event) throws IOException {
        HttpServletRequest request = event.getRequest();
        String query = request.getQueryString();
        String target = query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
        event.getResponse().sendRedirect(target);
    }
}
