package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import ua.edu.chnu.awards.common.web.PageResponse;
import ua.edu.chnu.awards.gdpr.dto.ActivityEntry;
import ua.edu.chnu.awards.gdpr.dto.PrivacySummary;
import ua.edu.chnu.awards.gdpr.repository.ActivityQueries;
import ua.edu.chnu.awards.gdpr.repository.PrivacySummaryQueries;

class PrivacyCentreServiceTest {

    private static final long USER = 7L;

    private final PrivacySummaryQueries summaryQueries = mock(PrivacySummaryQueries.class);
    private final ActivityQueries activityQueries = mock(ActivityQueries.class);
    private final PrivacyCentreService service = new PrivacyCentreService(summaryQueries, activityQueries);

    @Test
    void ac21_everyVisibilityIsReportedEvenWithoutAwards() {
        Instant exported = Instant.parse("2026-10-14T08:00:00Z");
        when(summaryQueries.awardsByVisibility(USER)).thenReturn(Map.of("UNIVERSITY", 2L));
        when(summaryQueries.documentsShown(USER)).thenReturn(1L);
        when(summaryQueries.emailOffTypes(USER)).thenReturn(List.of("AWARD_RETURNED"));
        when(summaryQueries.lastExportAt(USER)).thenReturn(exported);

        PrivacySummary summary = service.summary(USER);

        assertThat(summary.awardsByVisibility()).containsExactlyInAnyOrderEntriesOf(
            Map.of("PRIVATE", 0L, "UNIVERSITY", 2L, "PUBLIC", 0L));
        assertThat(summary.documentsShown()).isEqualTo(1L);
        assertThat(summary.emailOffTypes()).containsExactly("AWARD_RETURNED");
        assertThat(summary.lastExportAt()).isEqualTo(exported);
    }

    @Test
    void ac22_thePageSizeIsClampedAndANegativePageIsTheFirst() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(activityQueries.activity(eq(USER), pageable.capture()))
            .thenAnswer(call -> new PageImpl<ActivityEntry>(List.of(), call.getArgument(1), 0));

        service.activity(USER, -3, 500);
        PageResponse<ActivityEntry> small = service.activity(USER, 2, 0);

        assertThat(pageable.getAllValues()).extracting(Pageable::getPageNumber, Pageable::getPageSize)
            .containsExactly(tuple(0, PageResponse.MAX_SIZE), tuple(2, 1));
        assertThat(small.size()).isEqualTo(1);
    }
}
