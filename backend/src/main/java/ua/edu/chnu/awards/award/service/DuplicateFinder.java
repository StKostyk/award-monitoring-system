package ua.edu.chnu.awards.award.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.dto.DuplicateMatch;
import ua.edu.chnu.awards.award.entity.AwardStatus;

import lombok.RequiredArgsConstructor;

/**
 * Finds awards with the same award date and a similar title ({@code pg_trgm} similarity of the lower-cased
 * English or Ukrainian titles of at least 0.6): personal awards among the personal awards of the same owner,
 * unit awards among the awards of the same unit, whoever entered them, leaving out the drafts of other people,
 * which only their owner may see. Personal and unit awards are never compared with each other.
 */
@Component
@RequiredArgsConstructor
public class DuplicateFinder {

    static final double THRESHOLD = 0.6;

    private static final String QUERY = """
        select a.award_id as award_id, b.award_id as match_id, b.title, b.title_uk, b.award_date, b.status
          from awards a
          join awards b on b.award_date = a.award_date and b.award_id <> a.award_id
                       and (a.recipient_org_id is null and b.recipient_org_id is null and b.user_id = a.user_id
                         or b.recipient_org_id = a.recipient_org_id
                            and (b.user_id = a.user_id or b.status <> 'DRAFT'))
         where a.award_id in (:ids)
           and (similarity(lower(a.title), lower(b.title)) >= :threshold
             or similarity(lower(a.title_uk), lower(b.title_uk)) >= :threshold)
         order by a.award_id, b.award_id
        """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * The possible duplicates of each award.
     *
     * @param awardIds the awards to check
     * @return the matches by award id; awards without matches are absent
     */
    public Map<Long, List<DuplicateMatch>> matches(Collection<Long> awardIds) {
        Map<Long, List<DuplicateMatch>> found = new LinkedHashMap<>();
        if (awardIds.isEmpty()) {
            return found;
        }
        jdbc.query(QUERY, Map.of("ids", awardIds, "threshold", THRESHOLD), row -> {
            found.computeIfAbsent(row.getLong("award_id"), id -> new ArrayList<>()).add(new DuplicateMatch(
                row.getLong("match_id"), row.getString("title"), row.getString("title_uk"),
                row.getObject("award_date", LocalDate.class), AwardStatus.valueOf(row.getString("status"))));
        });
        return found;
    }
}
