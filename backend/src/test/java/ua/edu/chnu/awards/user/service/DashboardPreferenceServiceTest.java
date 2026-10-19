package ua.edu.chnu.awards.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.user.dto.DashboardPreferences;
import ua.edu.chnu.awards.user.repository.DashboardPreferenceQueries;

class DashboardPreferenceServiceTest {

    private final DashboardPreferenceQueries queries = mock(DashboardPreferenceQueries.class);
    private final DashboardPreferenceService service = new DashboardPreferenceService(queries);

    @Test
    void ac2_2_withoutASavedChoiceNothingIsHidden() {
        when(queries.find(5L)).thenReturn(Optional.empty());

        assertThat(service.preferences(5L).hiddenWidgets()).isEmpty();
    }

    @Test
    void ac2_2_theSavedChoiceIsReturned() {
        when(queries.find(5L)).thenReturn(Optional.of(List.of("TIMELINE", "BY_LEVEL")));

        assertThat(service.preferences(5L).hiddenWidgets()).containsExactly("TIMELINE", "BY_LEVEL");
    }

    @Test
    void ac2_2_duplicatesCollapseAndTheDisplayOrderIsKept() {
        DashboardPreferences stored = service.update(5L,
            new DashboardPreferences(List.of("RANK", "TIMELINE", "RANK", "SUMMARY")));

        assertThat(stored.hiddenWidgets()).containsExactly("SUMMARY", "TIMELINE", "RANK");
        verify(queries).save(5L, List.of("SUMMARY", "TIMELINE", "RANK"));
    }

    @Test
    void ac2_2_anEmptyOrMissingListShowsEveryWidget() {
        assertThat(service.update(5L, new DashboardPreferences(null)).hiddenWidgets()).isEmpty();

        verify(queries).save(5L, List.of());
    }

    @Test
    void ac2_2_anUnknownValueIsAnInvalidParameterAndNothingIsStored() {
        DashboardPreferences request = new DashboardPreferences(List.of("TIMELINE", "WEATHER"));

        assertThatThrownBy(() -> service.update(5L, request))
            .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                assertThat(problem.toProblem().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
                assertThat(problem.toProblem().getType().toString()).endsWith("invalid-parameter");
                assertThat(problem.toProblem().getProperties()).containsEntry("parameter", "hiddenWidgets");
            });
        verify(queries, never()).save(anyLong(), anyList());
    }

    @Test
    void ac2_2_aNullOrLowerCaseValueIsRejected() {
        DashboardPreferences nullValue = new DashboardPreferences(Arrays.asList("TIMELINE", null));
        DashboardPreferences lowerCase = new DashboardPreferences(List.of("timeline"));

        assertThatThrownBy(() -> service.update(5L, lowerCase)).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(() -> service.update(5L, nullValue)).isInstanceOf(ApiProblemException.class);
    }
}
