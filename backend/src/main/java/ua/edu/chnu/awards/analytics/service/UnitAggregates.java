package ua.edu.chnu.awards.analytics.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.analytics.dto.UnitFigures;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.award.service.UnitStatistics;
import ua.edu.chnu.awards.organization.OrganizationTree;

import lombok.RequiredArgsConstructor;

/**
 * Reads the aggregates of a unit's subtree for a period and its previous period, cached per unit and period.
 */
@Component
@RequiredArgsConstructor
public class UnitAggregates {

    /** Name of the cache. */
    public static final String CACHE = "unit-analytics";

    private final UnitStatistics statistics;
    private final OrganizationTree tree;
    private final Clock clock;

    /**
     * The aggregates of a unit.
     *
     * @param unitId   the unit, its subtree included
     * @param previous the previous period
     * @param period   the calendar years
     * @return the counts, with the time they were read
     */
    @Cacheable(cacheNames = CACHE, key = "#unitId + ':' + #period.from() + ':' + #period.to()")
    @Transactional(readOnly = true)
    public UnitFigures of(long unitId, YearRange previous, YearRange period) {
        Instant generatedAt = Instant.now(clock);
        Set<Long> subtree = tree.subtree(unitId);
        return new UnitFigures(generatedAt, statistics.counts(subtree, new YearRange(previous.from(), period.to())),
            statistics.owners(subtree, previous, period), statistics.sources(subtree, period),
            statistics.members(subtree));
    }
}
