package ua.edu.chnu.awards.user.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ua.edu.chnu.awards.user.dto.OnboardingFacts;
import ua.edu.chnu.awards.user.dto.OnboardingRequest;

import lombok.RequiredArgsConstructor;

/**
 * Reads and stores the getting-started progress of a person and the facts that decide the derived steps.
 */
@Repository
@RequiredArgsConstructor
public class OnboardingQueries {

    private static final String USER_ID = "userId";

    private static final String FIND = """
        select onboarding::text as onboarding from dashboard_preferences where user_id = :userId
        """;

    private static final String UPSERT = """
        insert into dashboard_preferences (user_id, onboarding, updated_at)
        values (:userId, cast(:onboarding as jsonb), now())
        on conflict (user_id)
        do update set onboarding = excluded.onboarding, updated_at = excluded.updated_at
        """;

    private static final String FACTS = """
        select exists (select 1 from consent_records
                        where user_id = :userId and consent_type = 'DATA_PROCESSING' and is_granted) as notice,
               exists (select 1 from awards where user_id = :userId) as award,
               exists (select 1 from role_delegations where delegator_id = :userId) as delegation
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    /**
     * The stored progress of a person.
     *
     * @param userId the person
     * @return the progress, if the person ever saved any
     */
    public Optional<OnboardingRequest> find(long userId) {
        return jdbc.query(FIND, Map.of(USER_ID, userId), (row, index) -> progress(row)).stream().findFirst();
    }

    /**
     * Stores the whole progress, inserting the row with the default dashboard choice when there is none.
     *
     * @param userId   the person
     * @param progress validated progress
     */
    public void save(long userId, OnboardingRequest progress) {
        try {
            jdbc.update(UPSERT, Map.of(USER_ID, userId, "onboarding", mapper.writeValueAsString(progress)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Onboarding progress cannot be written", e);
        }
    }

    /**
     * Whether the person acknowledged the privacy notice, owns an award and ever lent a role.
     *
     * @param userId the person
     * @return the facts
     */
    public OnboardingFacts facts(long userId) {
        return Objects.requireNonNull(jdbc.queryForObject(FACTS, Map.of(USER_ID, userId),
            (row, index) -> new OnboardingFacts(row.getBoolean("notice"), row.getBoolean("award"),
                row.getBoolean("delegation"))));
    }

    private OnboardingRequest progress(ResultSet row) throws SQLException {
        try {
            return mapper.readValue(row.getString("onboarding"), OnboardingRequest.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Onboarding progress cannot be read", e);
        }
    }
}
