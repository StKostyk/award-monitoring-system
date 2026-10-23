package ua.edu.chnu.awards.analytics.service;

import java.time.Instant;
import java.util.List;

import ua.edu.chnu.awards.analytics.dto.UnitFigures;
import ua.edu.chnu.awards.award.dto.CategoryRef;
import ua.edu.chnu.awards.award.dto.MemberCount;
import ua.edu.chnu.awards.award.dto.OwnerAwardCount;
import ua.edu.chnu.awards.award.dto.SourceSpelling;
import ua.edu.chnu.awards.award.dto.UnitAwardCount;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;

/**
 * Rows of cached aggregates for the unit analytics tests.
 */
final class UnitFiguresFixture {

    static final Instant GENERATED = Instant.parse("2026-10-19T09:58:00Z");
    static final YearRange PERIOD = new YearRange(2022, 2026);
    static final YearRange PREVIOUS = new YearRange(2017, 2021);
    static final CategoryRef MINISTRY = new CategoryRef(13L, "Ministry Recognition", "Відзнака міністерства",
        RecognitionLevel.NATIONAL);
    static final CategoryRef GRANT = new CategoryRef(20L, "Research Grant", "Грант", RecognitionLevel.FACULTY);
    static final CategoryRef DIPLOMA = new CategoryRef(21L, "Diploma", "Диплом", RecognitionLevel.FACULTY);

    private UnitFiguresFixture() {
    }

    static UnitFigures figures(List<UnitAwardCount> counts, List<OwnerAwardCount> owners,
                               List<SourceSpelling> sources, List<MemberCount> members) {
        return new UnitFigures(GENERATED, counts, owners, sources, members);
    }

    static UnitFigures empty() {
        return figures(List.of(), List.of(), List.of(), List.of());
    }

    static UnitAwardCount count(long organizationId, int year, CategoryRef category, boolean unitAward, long count,
                                long verified) {
        return new UnitAwardCount(organizationId, year, category, unitAward, count, verified);
    }

    static OwnerAwardCount owner(long userId, long organizationId, boolean current, long count) {
        return new OwnerAwardCount(userId, organizationId, current, count, RecognitionLevel.FACULTY);
    }

    static MemberCount members(long organizationId, long members) {
        return new MemberCount(organizationId, members);
    }

    static SourceSpelling spelling(String key, String spelling, long count) {
        return new SourceSpelling(key, spelling, count);
    }
}
