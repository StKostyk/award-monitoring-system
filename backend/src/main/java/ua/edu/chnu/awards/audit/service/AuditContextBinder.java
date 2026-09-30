package ua.edu.chnu.awards.audit.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.web.ClientRequest;

/**
 * Tells the table audit triggers who is acting: sets {@code app.current_user_id} (the access token subject) and
 * {@code app.correlation_id} for the current database transaction only, so a pooled connection never carries
 * them into the next transaction.
 */
@Component
public class AuditContextBinder {

    static final String BIND = "select set_config('app.current_user_id', ?, true), "
        + "set_config('app.correlation_id', ?, true)";

    /**
     * Binds the caller to the transaction just begun on the connection; does nothing without a signed-in
     * caller.
     *
     * @param connection the connection of the transaction
     * @throws SQLException when the settings cannot be written
     */
    public void bind(Connection connection) throws SQLException {
        Optional<String> actor = actor();
        if (actor.isEmpty()) {
            return;
        }
        UUID correlation = ClientRequest.current().correlationId();
        try (PreparedStatement statement = connection.prepareStatement(BIND)) {
            statement.setString(1, actor.get());
            statement.setString(2, correlation == null ? "" : correlation.toString());
            statement.execute();
        }
    }

    private static Optional<String> actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken token && token.isAuthenticated()
            ? Optional.ofNullable(token.getToken().getSubject())
            : Optional.empty();
    }
}
