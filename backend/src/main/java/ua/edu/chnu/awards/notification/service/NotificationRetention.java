package ua.edu.chnu.awards.notification.service;

import java.time.Duration;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.retention.AbstractDeletePolicy;
import ua.edu.chnu.awards.common.retention.RetentionOrderConstants;
import ua.edu.chnu.awards.common.retention.RetentionProperties;

/**
 * Deletes notifications whose expiry, set when they were sent, has passed.
 */
@Component
public class NotificationRetention extends AbstractDeletePolicy {

    private static final String DELETE = """
        delete from notifications where notification_id in (
            select notification_id from notifications where expires_at < :now limit :limit)
        """;

    private final RetentionProperties properties;

    /**
     * Creates the policy.
     *
     * @param jdbc       runs the batches
     * @param properties the period given to new notifications
     */
    public NotificationRetention(NamedParameterJdbcTemplate jdbc, RetentionProperties properties) {
        super(jdbc);
        this.properties = properties;
    }

    @Override
    public String key() {
        return "notifications";
    }

    @Override
    public int order() {
        return RetentionOrderConstants.NOTIFICATIONS;
    }

    @Override
    protected Duration retained() {
        return properties.notifications();
    }

    @Override
    protected String deleteBatch() {
        return DELETE;
    }
}
