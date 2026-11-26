package ua.edu.chnu.awards.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import ua.edu.chnu.awards.notification.entity.NotificationType;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;

class NotificationSchemaIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void ac1_1_bothTypeChecksListExactlyTheElevenTypes() {
        List<String> definitions = jdbc.queryForList("SELECT pg_get_constraintdef(oid) FROM pg_constraint"
            + " WHERE conname IN ('ck_notifications_type', 'ck_notification_preferences_event')", String.class);

        assertThat(definitions).hasSize(2).allSatisfy(definition -> {
            for (NotificationType type : NotificationType.values()) {
                assertThat(definition).contains("'" + type.name() + "'");
            }
            assertThat(definition.chars().filter(c -> c == '\'').count())
                .isEqualTo(2L * NotificationType.values().length);
            assertThat(definition).doesNotContain("AWARD_SUBMITTED", "REVIEW_ASSIGNED", "DEADLINE_REMINDER",
                "SYSTEM_ALERT");
        });
    }

    @Test
    void ac1_1_payloadAndDedupeKeyReplaceTitleAndMessage() {
        List<String> columns = jdbc.queryForList("SELECT column_name || ':' || data_type || ':' || is_nullable"
            + " || ':' || coalesce(character_maximum_length::text, '') FROM information_schema.columns"
            + " WHERE table_name = 'notifications'", String.class);

        assertThat(columns).contains("payload:jsonb:NO:", "dedupe_key:character varying:NO:64")
            .noneMatch(column -> column.startsWith("title:") || column.startsWith("message:"));
    }

    @Test
    void ac4_1_theDeliveryColumnsAndTheirCheckExist() {
        List<String> columns = jdbc.queryForList("SELECT column_name || ':' || data_type || ':' || is_nullable"
            + " || ':' || coalesce(character_maximum_length::text, '') FROM information_schema.columns"
            + " WHERE table_name = 'notifications'", String.class);
        String check = jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint"
            + " WHERE conname = 'ck_notifications_delivery_status'", String.class);

        assertThat(columns).contains("delivery_status:character varying:YES:20",
            "delivered_at:timestamp with time zone:YES:");
        assertThat(check).contains("'IN_APP'", "'EMAIL'", "'SENDING'", "'SENT'", "'FAILED'", "IS NULL");
    }

    @Test
    void ac1_1_theDedupeKeyIsUniqueAndTheListQueryHasItsIndex() {
        List<String> indexes = jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE tablename = 'notifications'",
            String.class);

        assertThat(indexes).anyMatch(index -> index.startsWith("CREATE UNIQUE INDEX uk_notifications_dedupe_key")
            && index.contains("(dedupe_key)"));
        assertThat(indexes).anyMatch(index -> index.contains("idx_notifications_user_channel_sent")
            && index.contains("(user_id, channel, sent_at DESC)"));
    }
}
