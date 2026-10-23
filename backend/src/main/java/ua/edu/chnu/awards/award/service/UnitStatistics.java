package ua.edu.chnu.awards.award.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import ua.edu.chnu.awards.award.dto.CategoryRef;
import ua.edu.chnu.awards.award.dto.MemberCount;
import ua.edu.chnu.awards.award.dto.OwnerAwardCount;
import ua.edu.chnu.awards.award.dto.SourceSpelling;
import ua.edu.chnu.awards.award.dto.UnitAwardCount;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;

import lombok.RequiredArgsConstructor;

/**
 * Counts the approved awards submitted in a set of organisations, personal and unit awards alike: an award counts
 * when it is approved, its organisation is one of the set and its award date lies in the period.
 */
@Service
@RequiredArgsConstructor
public class UnitStatistics {

    static final String COUNTS;
    static final String OWNERS;
    static final String SOURCES;

    private static final String ORGANIZATION_IDS = "organizationIds";

    private static final String COUNTED = """
        a.organization_id in (:organizationIds) and a.status = 'APPROVED'
           and a.award_date >= make_date(:from, 1, 1) and a.award_date < make_date(:to + 1, 1, 1)
        """;

    private static final String LEVEL_ORDER = Arrays.stream(RecognitionLevel.values())
        .map(level -> "'" + level.name() + "'").collect(Collectors.joining(", ", "array[", "]"));

    private static final String MEMBERS = """
        select organization_id, count(*) as members
          from users
         where organization_id in (:organizationIds) and account_status = 'ACTIVE'
         group by organization_id
        """;

    static {
        COUNTS = """
            select a.organization_id, cast(extract(year from a.award_date) as integer) as award_year,
                   c.category_id, c.name, c.name_uk, c.level, a.recipient_org_id is not null as unit_award,
                   count(*) as awards, count(*) filter (where a.verification_badge) as verified
              from awards a
              join award_categories c on c.category_id = a.category_id
             where\s""" + COUNTED + """
             group by a.organization_id, award_year, c.category_id, c.name, c.name_uk, c.level, unit_award
            """;
        OWNERS = """
            select a.user_id, a.organization_id, a.award_date >= make_date(:currentFrom, 1, 1) as current_period,
                   count(*) as awards, max(array_position(""" + LEVEL_ORDER + """
            , cast(c.level as text))) as level_rank
              from awards a
              join award_categories c on c.category_id = a.category_id
             where a.recipient_org_id is null and\s""" + COUNTED + """
             group by a.user_id, a.organization_id, current_period
            """;
        SOURCES = """
            select lower(regexp_replace(btrim(a.awarding_organization), '\\s+', ' ', 'g')) as source_key,
                   btrim(a.awarding_organization) as spelling, count(*) as awards
              from awards a
             where btrim(a.awarding_organization) <> '' and\s""" + COUNTED + """
             group by source_key, spelling
            """;
    }

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Counted awards per organisation, year, category and recipient kind; groups without awards are absent.
     *
     * @param organizationIds the organisations of the submission
     * @param period          the calendar years
     * @return the counts
     */
    public List<UnitAwardCount> counts(Collection<Long> organizationIds, YearRange period) {
        if (organizationIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(COUNTS, counted(organizationIds, period), (row, index) -> new UnitAwardCount(
            row.getLong("organization_id"), row.getInt("award_year"),
            new CategoryRef(row.getLong("category_id"), row.getString("name"), row.getString("name_uk"),
                RecognitionLevel.valueOf(row.getString("level"))),
            row.getBoolean("unit_award"), row.getLong("awards"), row.getLong("verified")));
    }

    /**
     * Counted personal awards per owner and organisation, split into the previous period and the period.
     *
     * @param organizationIds the organisations of the submission
     * @param previous        the previous period, ending the year before the period starts
     * @param period          the calendar years
     * @return the counts with the highest level
     */
    public List<OwnerAwardCount> owners(Collection<Long> organizationIds, YearRange previous, YearRange period) {
        if (organizationIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource parameters = counted(organizationIds, new YearRange(previous.from(), period.to()))
            .addValue("currentFrom", period.from());
        return jdbc.query(OWNERS, parameters, (row, index) -> new OwnerAwardCount(row.getLong("user_id"),
            row.getLong("organization_id"), row.getBoolean("current_period"), row.getLong("awards"),
            RecognitionLevel.values()[row.getInt("level_rank") - 1]));
    }

    /**
     * Counted awards per spelling of the awarding organisation; blank spellings are left out.
     *
     * @param organizationIds the organisations of the submission
     * @param period          the calendar years
     * @return the counts with the grouping key
     */
    public List<SourceSpelling> sources(Collection<Long> organizationIds, YearRange period) {
        if (organizationIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(SOURCES, counted(organizationIds, period), (row, index) ->
            new SourceSpelling(row.getString("source_key"), row.getString("spelling"), row.getLong("awards")));
    }

    /**
     * Active accounts per organisation; organisations without any are absent.
     *
     * @param organizationIds the organisations
     * @return the counts
     */
    public List<MemberCount> members(Collection<Long> organizationIds) {
        if (organizationIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(MEMBERS, new MapSqlParameterSource(ORGANIZATION_IDS, organizationIds), (row, index) ->
            new MemberCount(row.getLong("organization_id"), row.getLong("members")));
    }

    private static MapSqlParameterSource counted(Collection<Long> organizationIds, YearRange period) {
        return new MapSqlParameterSource(ORGANIZATION_IDS, organizationIds).addValue("from", period.from())
            .addValue("to", period.to());
    }
}
