package ua.edu.chnu.awards.auth.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/**
 * A login form posted with a stale CSRF token (the server restarted or the session ended while the page was
 * open) goes back to the form with an explanation instead of a bare 403.
 */
@Component
public class LoginAccessDeniedHandler implements AccessDeniedHandler {

    static final String EXPIRED = "EXPIRED";

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        String login = request.getContextPath() + "/login";
        if (exception instanceof CsrfException && login.equals(request.getRequestURI())) {
            response.sendRedirect(login + "?error=" + EXPIRED);
            return;
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }
}
