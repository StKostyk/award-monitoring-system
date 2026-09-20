package ua.edu.chnu.awards.auth.security;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Signs a user out everywhere: deletes every authorization (codes, access and refresh tokens) the authorization
 * server holds for the principal and expires the login sessions of the server itself, so a browser that was
 * signed in cannot obtain a new authorization without the password. {@code OAuth2AuthorizationService} has no
 * lookup by principal, so the rows are deleted directly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthorizationRevoker {

    private final JdbcTemplate jdbc;
    private final SessionRegistry sessions;

    /**
     * Deletes all authorizations of the principal and expires its login sessions.
     *
     * @param principalName the user's email address as stored by the authorization server
     * @return number of authorizations removed
     */
    public int revokeAll(String principalName) {
        List<SessionInformation> logins = sessions.getAllPrincipals().stream()
            .filter(principal -> principal instanceof UserDetails user
                && user.getUsername().equalsIgnoreCase(principalName))
            .flatMap(principal -> sessions.getAllSessions(principal, false).stream())
            .toList();
        logins.forEach(SessionInformation::expireNow);
        int authorizations = jdbc.update("delete from oauth2_authorization where principal_name = ?", principalName);
        log.debug("Revoked {} authorizations and {} login sessions of {}", authorizations, logins.size(),
            principalName);
        return authorizations;
    }
}
