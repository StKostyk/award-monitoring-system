package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.award.dto.AwardWarning;
import ua.edu.chnu.awards.award.dto.DuplicateMatch;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardStatus;

class AwardWarningsTest {

    private static final LocalDate KYIV_TODAY = LocalDate.of(2026, 9, 28);

    private final DuplicateFinder duplicates = mock(DuplicateFinder.class);
    private final AwardWarnings warnings = new AwardWarnings(new AwardDateRules(Clock.fixed(
        Instant.parse("2026-09-28T09:00:00Z"), ZoneId.of("Europe/Kyiv"))), duplicates);
    private final DuplicateMatch match = new DuplicateMatch(9L, null, "Грамота МОН", KYIV_TODAY.minusYears(1),
        AwardStatus.PENDING);

    @Test
    void ac2_3_aRecentDraftCarriesTheRecentDateWarning() {
        Award recent = draft(1L, KYIV_TODAY.minusDays(7));
        Award older = draft(2L, KYIV_TODAY.minusYears(1));
        when(duplicates.matches(Set.of(1L, 2L))).thenReturn(Map.of());

        Map<Long, List<AwardWarning>> found = warnings.forDrafts(List.of(recent, older));

        assertThat(found.get(1L)).containsExactly(new AwardWarning("RECENT_DATE", "awardDate", List.of()));
        assertThat(found.get(2L)).isEmpty();
    }

    @Test
    void ac2_4_aPossibleDuplicateListsTheMatchingAwards() {
        Award draft = draft(1L, KYIV_TODAY.minusYears(1));
        when(duplicates.matches(Set.of(1L))).thenReturn(Map.of(1L, List.of(match)));

        assertThat(warnings.of(draft))
            .containsExactly(new AwardWarning("POSSIBLE_DUPLICATE", "title", List.of(match)));
    }

    @Test
    void ac2_4_submittedAwardsCarryNoWarningsAndAreNotCompared() {
        Award pending = draft(1L, KYIV_TODAY);
        pending.setStatus(AwardStatus.PENDING);

        assertThat(warnings.of(pending)).isEmpty();
        verify(duplicates, never()).matches(anyCollection());
    }

    private static Award draft(long id, LocalDate date) {
        return Award.builder().id(id).titleUk("Грамота МОН").awardDate(date).build();
    }
}
