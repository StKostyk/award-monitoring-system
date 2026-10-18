package ua.edu.chnu.awards.auth.service;

import java.time.Duration;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.retention.AbstractDeletePolicy;
import ua.edu.chnu.awards.common.retention.RetentionOrderConstants;
import ua.edu.chnu.awards.common.retention.RetentionProperties;

/**
 * Deletes one-time tokens used or expired longer ago than the period.
 */
@Component
public class OneTimeTokenRetention extends AbstractDeletePolicy {

    private static final String DELETE = """
        delete from one_time_tokens where id in (
            select id from one_time_tokens where used_at < :before or expires_at < :before limit :limit)
        """;

    private final RetentionProperties properties;

    /**
     * Creates the policy.
     *
     * @param jdbc       runs the batches
     * @param properties the period
     */
    public OneTimeTokenRetention(NamedParameterJdbcTemplate jdbc, RetentionProperties properties) {
        super(jdbc);
        this.properties = properties;
    }

    @Override
    public String key() {
        return "one-time-tokens";
    }

    @Override
    public int order() {
        return RetentionOrderConstants.ONE_TIME_TOKENS;
    }

    @Override
    protected Duration retained() {
        return properties.oneTimeTokens();
    }

    @Override
    protected String deleteBatch() {
        return DELETE;
    }
}
