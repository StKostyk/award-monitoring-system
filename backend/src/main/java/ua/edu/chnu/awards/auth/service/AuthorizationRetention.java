package ua.edu.chnu.awards.auth.service;

import java.time.Duration;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.retention.AbstractDeletePolicy;
import ua.edu.chnu.awards.common.retention.RetentionOrderConstants;
import ua.edu.chnu.awards.common.retention.RetentionProperties;

/**
 * Deletes authorizations whose every code and token expired longer ago than the period; one with a live refresh
 * token stays.
 */
@Component
public class AuthorizationRetention extends AbstractDeletePolicy {

    private static final String DELETE = """
        delete from oauth2_authorization where id in (
            select id from oauth2_authorization
             where coalesce(greatest(authorization_code_expires_at, access_token_expires_at,
                                     oidc_id_token_expires_at, refresh_token_expires_at,
                                     user_code_expires_at, device_code_expires_at), '-infinity') < :before
             limit :limit)
        """;

    private final RetentionProperties properties;

    /**
     * Creates the policy.
     *
     * @param jdbc       runs the batches
     * @param properties the period
     */
    public AuthorizationRetention(NamedParameterJdbcTemplate jdbc, RetentionProperties properties) {
        super(jdbc);
        this.properties = properties;
    }

    @Override
    public String key() {
        return "authorizations";
    }

    @Override
    public int order() {
        return RetentionOrderConstants.AUTHORIZATIONS;
    }

    @Override
    protected Duration retained() {
        return properties.authorizations();
    }

    @Override
    protected String deleteBatch() {
        return DELETE;
    }
}
