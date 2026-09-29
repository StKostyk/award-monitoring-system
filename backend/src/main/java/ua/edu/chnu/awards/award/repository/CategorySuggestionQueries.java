package ua.edu.chnu.awards.award.repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import ua.edu.chnu.awards.award.entity.RecognitionLevel;

import lombok.RequiredArgsConstructor;

/**
 * Reads what the category suggestion needs: active categories with their keywords and the categories a person
 * used most.
 */
@Repository
@RequiredArgsConstructor
public class CategorySuggestionQueries {

    private static final String CATEGORIES = """
        select category_id, name, name_uk, level, parent_category_id, sort_order, keywords
          from award_categories
         where is_active
         order by sort_order, name
        """;

    private static final String MOST_USED = """
        select a.category_id
          from awards a
          join award_categories c on c.category_id = a.category_id and c.is_active
         where a.user_id = :userId
         group by a.category_id
         order by count(*) desc, max(a.updated_at) desc, a.category_id
         limit :limit
        """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Active categories in display order.
     *
     * @return the categories with their keywords
     */
    public List<Candidate> activeCategories() {
        return jdbc.query(CATEGORIES, (row, index) -> new Candidate(row.getLong("category_id"), row.getString("name"),
            row.getString("name_uk"), RecognitionLevel.valueOf(row.getString("level")),
            row.getObject("parent_category_id", Long.class), row.getInt("sort_order"), keywords(row)));
    }

    /**
     * Categories of a person's awards, the most used first.
     *
     * @param userId the owner of the awards
     * @param limit  how many categories at most
     * @return category ids
     */
    public List<Long> mostUsedCategories(long userId, int limit) {
        return jdbc.queryForList(MOST_USED, Map.of("userId", userId, "limit", limit), Long.class);
    }

    private static List<String> keywords(ResultSet row) throws SQLException {
        Array array = row.getArray("keywords");
        try {
            return Arrays.stream((Object[]) array.getArray()).map(String.class::cast).toList();
        } finally {
            array.free();
        }
    }

    /**
     * An active category as the suggestion scores it.
     *
     * @param id        identifier
     * @param name      English name
     * @param nameUk    Ukrainian name
     * @param level     recognition level
     * @param parentId  parent category, null for the root of a level
     * @param sortOrder display order
     * @param keywords  lower-case stems
     */
    public record Candidate(long id, String name, String nameUk, RecognitionLevel level, Long parentId,
                            int sortOrder, List<String> keywords) {

        /**
         * Keeps an unmodifiable copy of the keywords.
         */
        public Candidate {
            keywords = List.copyOf(keywords);
        }
    }
}
