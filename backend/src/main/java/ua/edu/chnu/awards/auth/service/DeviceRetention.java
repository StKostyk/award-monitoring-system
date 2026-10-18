package ua.edu.chnu.awards.auth.service;

import java.time.Duration;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.retention.AbstractDeletePolicy;
import ua.edu.chnu.awards.common.retention.RetentionOrderConstants;
import ua.edu.chnu.awards.common.retention.RetentionProperties;

/**
 * Deletes devices last used longer ago than the period; the next sign-in from one counts as a new device.
 */
@Component
public class DeviceRetention extends AbstractDeletePolicy {

    private static final String DELETE = """
        delete from user_devices where id in (
            select id from user_devices where last_used_at < :before limit :limit)
        """;

    private final RetentionProperties properties;

    /**
     * Creates the policy.
     *
     * @param jdbc       runs the batches
     * @param properties the period
     */
    public DeviceRetention(NamedParameterJdbcTemplate jdbc, RetentionProperties properties) {
        super(jdbc);
        this.properties = properties;
    }

    @Override
    public String key() {
        return "devices";
    }

    @Override
    public int order() {
        return RetentionOrderConstants.DEVICES;
    }

    @Override
    protected Duration retained() {
        return properties.devices();
    }

    @Override
    protected String deleteBatch() {
        return DELETE;
    }
}
