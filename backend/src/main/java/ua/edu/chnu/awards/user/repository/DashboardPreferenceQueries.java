package ua.edu.chnu.awards.user.repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Reads, stores and deletes the widget choice of the personal dashboard.
 */
@Repository
@RequiredArgsConstructor
public class DashboardPreferenceQueries {

    private static final String FIND = """
        select hidden_widgets from dashboard_preferences where user_id = :userId
        """;

    private static final String UPSERT = """
        insert into dashboard_preferences (user_id, hidden_widgets, updated_at)
        values (:userId, string_to_array(:widgets, ','), now())
        on conflict (user_id)
        do update set hidden_widgets = excluded.hidden_widgets, updated_at = excluded.updated_at
        """;

    private static final String DELETE = "delete from dashboard_preferences where user_id = :userId";

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * The stored choice of a person.
     *
     * @param userId the person
     * @return the hidden widget names, if the person ever saved a choice
     */
    public Optional<List<String>> find(long userId) {
        return jdbc.query(FIND, Map.of("userId", userId), (row, index) -> widgets(row)).stream().findFirst();
    }

    /**
     * Stores the choice, inserting the row or replacing it.
     *
     * @param userId  the person
     * @param widgets widget names without commas
     */
    public void save(long userId, List<String> widgets) {
        jdbc.update(UPSERT, Map.of("userId", userId, "widgets", String.join(",", widgets)));
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

    private static List<String> widgets(ResultSet row) throws SQLException {
        Array array = row.getArray("hidden_widgets");
        try {
            return Arrays.stream((Object[]) array.getArray()).map(String.class::cast).toList();
        } finally {
            array.free();
        }
    }
}
