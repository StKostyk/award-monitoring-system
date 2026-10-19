package ua.edu.chnu.awards.award.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import ua.edu.chnu.awards.award.dto.CategoryRef;
import ua.edu.chnu.awards.award.dto.CategoryYearCount;
import ua.edu.chnu.awards.award.dto.CountedTotals;
import ua.edu.chnu.awards.award.dto.DepartmentStanding;
import ua.edu.chnu.awards.award.dto.OpenAwards;
import ua.edu.chnu.awards.award.dto.TimelineEntry;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;

import lombok.RequiredArgsConstructor;

/**
 * Counts the approved personal awards of a person, the counted awards: an award counts when it is approved, has no
 * recipient unit and its award date lies in the period.
 */
@Service
@RequiredArgsConstructor
public class AwardStatistics {

    static final String STANDING = """
        with members as materialized (
            select u.user_id,
                   (select count(*)
                      from awards a
                     where a.user_id = u.user_id and a.status = 'APPROVED' and a.recipient_org_id is null
                       and a.award_date >= make_date(:from, 1, 1)
                       and a.award_date < make_date(:to + 1, 1, 1)) as awards
              from users u
             where u.organization_id = :departmentId and u.account_status = 'ACTIVE'
        )
        select count(*) as group_size,
               count(*) filter (where awards > :count) as ahead,
               count(*) filter (where awards = :count and user_id <> :userId) as tied
          from members
        """;

    private static final String USER_ID = "userId";
    private static final String FROM = "from";
    private static final String TO = "to";

    private static final String COUNTED = """
        a.user_id = :userId and a.status = 'APPROVED' and a.recipient_org_id is null
           and a.award_date >= make_date(:from, 1, 1) and a.award_date < make_date(:to + 1, 1, 1)
        """;

    private static final String FIRST_YEAR = """
        select cast(extract(year from min(award_date)) as integer)
          from awards
         where user_id = :userId and status = 'APPROVED' and recipient_org_id is null
        """;

    private static final String TOTALS = """
        select count(*) as approved, count(*) filter (where a.verification_badge) as verified,
               min(a.award_date) as first_date, max(a.award_date) as latest_date
          from awards a
         where\s""" + COUNTED;

    private static final String BY_CATEGORY_AND_YEAR = """
        select cast(extract(year from a.award_date) as integer) as award_year, c.category_id, c.name, c.name_uk,
               c.level, count(*) as awards
          from awards a
          join award_categories c on c.category_id = a.category_id
         where\s""" + COUNTED + """
         group by award_year, c.category_id, c.name, c.name_uk, c.level
         order by award_year, c.category_id
        """;

    private static final String TIMELINE = """
        select a.award_id, a.title, a.title_uk, a.award_date, a.awarding_organization, a.verification_badge,
               c.category_id, c.name, c.name_uk, c.level
          from awards a
          join award_categories c on c.category_id = a.category_id
         where\s""" + COUNTED + """
         order by a.award_date desc, a.award_id desc
         limit :limit
        """;

    private static final String OPEN = """
        select count(*) filter (where status = 'DRAFT') as drafts,
               count(*) filter (where status = 'PENDING') as pending
          from awards
         where user_id = :userId and recipient_org_id is null and status in ('DRAFT', 'PENDING')
        """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * The calendar year of the earliest approved personal award of a person, whatever the period.
     *
     * @param userId the award owner
     * @return the year, empty without such awards
     */
    public OptionalInt firstYear(long userId) {
        Integer year = jdbc.queryForObject(FIRST_YEAR, Map.of(USER_ID, userId), Integer.class);
        return year == null ? OptionalInt.empty() : OptionalInt.of(year);
    }

    /**
     * Totals of the counted awards of a person.
     *
     * @param userId the award owner
     * @param period the calendar years
     * @return the totals
     */
    public CountedTotals totals(long userId, YearRange period) {
        return Objects.requireNonNull(jdbc.queryForObject(TOTALS, counted(userId, period), (row, index) ->
            new CountedTotals(row.getLong("approved"), row.getLong("verified"),
                row.getObject("first_date", LocalDate.class), row.getObject("latest_date", LocalDate.class))));
    }

    /**
     * Counted awards of a person per year and category; pairs without awards are absent.
     *
     * @param userId the award owner
     * @param period the calendar years
     * @return the counts, by year and category id
     */
    public List<CategoryYearCount> byCategoryAndYear(long userId, YearRange period) {
        return jdbc.query(BY_CATEGORY_AND_YEAR, counted(userId, period), (row, index) ->
            new CategoryYearCount(row.getInt("award_year"), category(row), row.getLong("awards")));
    }

    /**
     * Counted awards of a person, newest award date first.
     *
     * @param userId the award owner
     * @param period the calendar years
     * @param limit  the most entries to return
     * @return the entries
     */
    public List<TimelineEntry> timeline(long userId, YearRange period, int limit) {
        return jdbc.query(TIMELINE, counted(userId, period).addValue("limit", limit), (row, index) ->
            new TimelineEntry(row.getLong("award_id"), row.getString("title"), row.getString("title_uk"),
                row.getObject("award_date", LocalDate.class), category(row), row.getString("awarding_organization"),
                row.getBoolean("verification_badge")));
    }

    /**
     * Drafts and pending personal awards of a person, whatever their dates.
     *
     * @param userId the award owner
     * @return the counts
     */
    public OpenAwards openAwards(long userId) {
        return Objects.requireNonNull(jdbc.queryForObject(OPEN, Map.of(USER_ID, userId), (row, index) ->
            new OpenAwards(row.getLong("drafts"), row.getLong("pending"))));
    }

    /**
     * Where a person's count of counted awards stands among the active accounts of a department, each ranked by its
     * own counted awards in the same period.
     *
     * @param departmentId the department
     * @param userId       the person, left out of the tied members
     * @param count        the person's counted awards
     * @param period       the calendar years
     * @return the group size and the members ahead and level
     */
    public DepartmentStanding standing(long departmentId, long userId, long count, YearRange period) {
        MapSqlParameterSource parameters = counted(userId, period).addValue("departmentId", departmentId)
            .addValue("count", count);
        return Objects.requireNonNull(jdbc.queryForObject(STANDING, parameters, (row, index) ->
            new DepartmentStanding(row.getLong("group_size"), row.getLong("ahead"), row.getLong("tied"))));
    }

    private static MapSqlParameterSource counted(long userId, YearRange period) {
        return new MapSqlParameterSource(USER_ID, userId).addValue(FROM, period.from()).addValue(TO, period.to());
    }

    private static CategoryRef category(ResultSet row) throws SQLException {
        return new CategoryRef(row.getLong("category_id"), row.getString("name"), row.getString("name_uk"),
            RecognitionLevel.valueOf(row.getString("level")));
    }
}
