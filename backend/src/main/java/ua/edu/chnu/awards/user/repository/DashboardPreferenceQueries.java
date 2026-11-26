package ua.edu.chnu.awards.user.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import ua.edu.chnu.awards.user.dto.DashboardLayout;
import ua.edu.chnu.awards.user.dto.DashboardPreferences;

import lombok.RequiredArgsConstructor;

/**
 * Reads, stores and deletes the colour scheme and the dashboard layouts of a person.
 */
@Repository
@RequiredArgsConstructor
public class DashboardPreferenceQueries {

    private static final TypeReference<Map<String, DashboardLayout>> LAYOUTS = new TypeReference<>() {
    };

    private static final String FIND = """
        select color_scheme, layouts::text as layouts from dashboard_preferences where user_id = :userId
        """;

    private static final String UPSERT = """
        insert into dashboard_preferences (user_id, color_scheme, layouts, updated_at)
        values (:userId, :colorScheme, cast(:layouts as jsonb), now())
        on conflict (user_id)
        do update set color_scheme = excluded.color_scheme, layouts = excluded.layouts,
                      updated_at = excluded.updated_at
        """;

    private static final String DELETE = "delete from dashboard_preferences where user_id = :userId";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    /**
     * The stored choice of a person.
     *
     * @param userId the person
     * @return the scheme and the layouts, if the person ever saved a choice
     */
    public Optional<DashboardPreferences> find(long userId) {
        return jdbc.query(FIND, Map.of("userId", userId), (row, index) -> preferences(row)).stream().findFirst();
    }

    /**
     * Stores the whole choice, inserting the row or replacing it.
     *
     * @param userId      the person
     * @param preferences a validated scheme and layouts
     */
    public void save(long userId, DashboardPreferences preferences) {
        jdbc.update(UPSERT, Map.of("userId", userId, "colorScheme", preferences.colorScheme(),
            "layouts", write(preferences.layouts())));
    }

    /**
     * Deletes the choice of a person.
     *
     * @param userId the person
     * @return the number of rows deleted
     */
    public int delete(long userId) {
        return jdbc.update(DELETE, Map.of("userId", userId));
    }

    private DashboardPreferences preferences(ResultSet row) throws SQLException {
        try {
            return new DashboardPreferences(row.getString("color_scheme"),
                mapper.readValue(row.getString("layouts"), LAYOUTS));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Dashboard layouts cannot be read", e);
        }
    }

    private String write(Map<String, DashboardLayout> layouts) {
        try {
            return mapper.writeValueAsString(layouts);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Dashboard layouts cannot be written", e);
        }
    }
}
