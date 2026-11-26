package ua.edu.chnu.awards.notification.service;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.erasure.ErasureContext;
import ua.edu.chnu.awards.common.erasure.ErasureContributor;
import ua.edu.chnu.awards.common.erasure.ErasureOrderConstants;

import lombok.RequiredArgsConstructor;

/**
 * Erasure of the person's notifications, preferences and undelivered events, and of the person's name in other
 * people's notifications about an award the person reviewed or corrected or a delegation they were party to; a
 * namesake in any other notification keeps their name.
 */
@Component
@RequiredArgsConstructor
public class NotificationErasure implements ErasureContributor {

    private static final String RENAME = """
        update notifications n
           set payload = fn_jsonb_replace_strings(n.payload, :names, :pseudonym)
         where n.user_id <> :userId
           and ((n.reference_type = 'AWARD' and n.reference_id in (
                    select r.award_id from review_decisions d
                      join award_requests r on r.request_id = d.request_id
                     where d.reviewer_id = :userId or d.delegator_id = :userId
                    union select v.award_id from award_versions v where v.actor_id = :userId
                    union select r.award_id from award_requests r where r.current_reviewer_id = :userId))
             or (n.type in ('DELEGATION_CREATED', 'DELEGATION_REVOKED') and n.user_id in (
                    select delegate_id from role_delegations where delegator_id = :userId
                    union select delegator_id from role_delegations where delegate_id = :userId)))
           and fn_jsonb_replace_strings(n.payload, :names, :pseudonym) <> n.payload
        returning n.notification_id
        """;

    private static final String DELETE_NOTIFICATIONS = "delete from notifications where user_id = :userId";

    private static final String DELETE_PREFERENCES = "delete from notification_preferences where user_id = :userId";

    private static final String DELETE_PUBLICATIONS = """
        delete from event_publication
         where completion_date is null
           and (serialized_event ~ ('"[A-Za-z]*[uU]serId":' || :userId || '[,}]')
                or strpos(lower(serialized_event), lower(:quotedEmail)) > 0)
        """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public int order() {
        return ErasureOrderConstants.NOTIFICATIONS;
    }

    @Override
    public void erase(ErasureContext context) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", context.userId())
            .addValue("names", context.names().toArray(String[]::new))
            .addValue("pseudonym", context.pseudonym())
            .addValue("quotedEmail", "\"" + context.emailAddress() + "\"");
        List<Long> renamed = jdbc.queryForList(RENAME, params, Long.class);
        context.erased("notifications", renamed);
        context.count("notificationsRenamed", renamed.size());
        context.count("notificationsDeleted", jdbc.update(DELETE_NOTIFICATIONS, params));
        context.count("preferencesDeleted", jdbc.update(DELETE_PREFERENCES, params));
        context.count("publicationsDeleted", jdbc.update(DELETE_PUBLICATIONS, params));
    }
}
