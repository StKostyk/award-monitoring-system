package ua.edu.chnu.awards.auth.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Sends the user back to the login page with an error code the page can translate.
 */
@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    static final String BAD_CREDENTIALS = "BAD_CREDENTIALS";

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String code = exception instanceof AccountStatusRefusedException refused
            ? refused.getStatus().name()
            : BAD_CREDENTIALS;
        getRedirectStrategy().sendRedirect(request, response, "/login?error=" + code);
    }
}
