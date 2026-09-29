package ua.edu.chnu.awards.auth.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * Completes a logout request the OpenID Connect endpoint refused. The library refuses an {@code id_token_hint}
 * whose authorization was deleted by a sign-out everywhere, and one whose {@code sid} names another login session
 * of the same user (it takes the user's most recent session when the token is issued). In both cases the browser
 * asked to sign out, so its own login session is ended and the login page shown, instead of an error page and a
 * session that signs the user straight back in.
 */
@Slf4j
public class RefusedLogoutHandler implements AuthenticationFailureHandler {

    private final SecurityContextLogoutHandler sessionLogout = new SecurityContextLogoutHandler();

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.debug("Logout request refused, ending the requesting session: {}", exception.getMessage());
        sessionLogout.logout(request, response, null);
        response.sendRedirect(request.getContextPath() + "/login");
    }
}
