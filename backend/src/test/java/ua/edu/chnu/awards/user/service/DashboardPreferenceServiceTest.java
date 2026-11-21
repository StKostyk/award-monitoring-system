package ua.edu.chnu.awards.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.user.dto.Dashboard;
import ua.edu.chnu.awards.user.dto.DashboardLayout;
import ua.edu.chnu.awards.user.dto.DashboardPreferences;
import ua.edu.chnu.awards.user.dto.DashboardWidget;
import ua.edu.chnu.awards.user.repository.DashboardPreferenceQueries;

class DashboardPreferenceServiceTest {

    private final DashboardPreferenceQueries queries = mock(DashboardPreferenceQueries.class);
    private final DashboardPreferenceService service = new DashboardPreferenceService(queries);

    @Test
    void ac1_1_withoutASavedChoiceTheSchemeIsSystemAndEveryLayoutTheDefault() {
        when(queries.find(5L)).thenReturn(Optional.empty());

        DashboardPreferences preferences = service.preferences(5L);

        assertThat(preferences.colorScheme()).isEqualTo("SYSTEM");
        assertThat(preferences.layouts()).isEmpty();
    }

    @Test
    void ac1_1_theSavedChoiceIsReturned() {
        DashboardPreferences stored = new DashboardPreferences("DARK",
            Map.of("MY_AWARDS", new DashboardLayout(List.of("RANK"), List.of("KPI"))));
        when(queries.find(5L)).thenReturn(Optional.of(stored));

        assertThat(service.preferences(5L)).isEqualTo(stored);
    }

    @Test
    void ac1_3_theSchemeAndTheLayoutsAreStoredInDashboardOrder() {
        DashboardPreferences request = new DashboardPreferences("DARK", Map.of(
            "COMMAND_CENTRE", new DashboardLayout(List.of("UNITS", "TODAY"), List.of()),
            "MY_AWARDS", new DashboardLayout(List.of("RANK", "KPI"), List.of("TREND"))));

        DashboardPreferences stored = service.update(5L, request);

        assertThat(stored.colorScheme()).isEqualTo("DARK");
        assertThat(stored.layouts()).containsOnlyKeys("MY_AWARDS", "COMMAND_CENTRE");
        assertThat(stored.layouts().keySet()).containsExactly("MY_AWARDS", "COMMAND_CENTRE");
        assertThat(stored.layouts().get("MY_AWARDS").order()).containsExactly("RANK", "KPI");
        verify(queries).save(5L, stored);
    }

    @Test
    void ac1_3_anEmptyBodyKeepsTheSystemSchemeAndTheDefaultLayouts() {
        Map<String, DashboardLayout> withNullLayout = new HashMap<>();
        withNullLayout.put("HALL_OF_FAME", null);

        DashboardPreferences stored = service.update(5L, new DashboardPreferences(null, withNullLayout));

        assertThat(stored.colorScheme()).isEqualTo("SYSTEM");
        assertThat(stored.layouts()).isEmpty();
    }

    @Test
    void ac1_3_aLayoutWithoutListsIsStoredWithEmptyLists() {
        DashboardPreferences stored = service.update(5L,
            new DashboardPreferences("LIGHT", Map.of("HALL_OF_FAME", new DashboardLayout(null, null))));

        assertThat(stored.layouts().get("HALL_OF_FAME")).isEqualTo(new DashboardLayout(List.of(), List.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dark", "BLUE", ""})
    void ac1_4_anUnknownSchemeNamesColorScheme(String scheme) {
        assertRejected(new DashboardPreferences(scheme, Map.of()), "colorScheme");
    }

    @Test
    void ac1_4_anUnknownDashboardNamesLayouts() {
        assertRejected(new DashboardPreferences("DARK",
            Map.of("ADMIN", new DashboardLayout(List.of(), List.of()))), "layouts");
    }

    @Test
    void ac1_4_aWidgetOfAnotherDashboardNamesTheOrderOfThatDashboard() {
        assertRejected(new DashboardPreferences("DARK",
            Map.of("HALL_OF_FAME", new DashboardLayout(List.of("KPI", "QUEUE"), List.of()))),
            "layouts.HALL_OF_FAME.order");
    }

    @Test
    void ac1_4_aDuplicateInHiddenNamesTheHiddenList() {
        assertRejected(new DashboardPreferences("DARK",
            Map.of("COMMAND_CENTRE", new DashboardLayout(List.of(), List.of("TOP", "TOP")))),
            "layouts.COMMAND_CENTRE.hidden");
    }

    @Test
    void ac1_4_aDuplicateInOrderNamesTheOrder() {
        assertRejected(new DashboardPreferences("DARK",
            Map.of("MY_AWARDS", new DashboardLayout(List.of("RANK", "KPI", "RANK"), List.of()))),
            "layouts.MY_AWARDS.order");
    }

    @Test
    void ac1_4_aNullOrLowerCaseWidgetIsRejected() {
        assertRejected(new DashboardPreferences("DARK",
            Map.of("MY_AWARDS", new DashboardLayout(Arrays.asList("KPI", null), List.of()))),
            "layouts.MY_AWARDS.order");
        assertRejected(new DashboardPreferences("DARK",
            Map.of("MY_AWARDS", new DashboardLayout(List.of(), List.of("kpi")))), "layouts.MY_AWARDS.hidden");
    }

    @Test
    void ac1_4_everyWidgetOfADashboardIsAKnownWidget() {
        for (Dashboard dashboard : Dashboard.values()) {
            assertThat(dashboard.widgets()).doesNotHaveDuplicates();
            assertThat(dashboard.widgets()).allSatisfy(widget -> assertThat(dashboard.holds(widget.name())).isTrue());
        }
        assertThat(Dashboard.MY_AWARDS.holds(DashboardWidget.QUEUE.name())).isFalse();
        assertThat(Dashboard.MY_AWARDS.holds(DashboardWidget.FACT.name())).isTrue();
        assertThat(Dashboard.MY_AWARDS.holds(DashboardWidget.LATEST.name())).isTrue();
        assertThat(Dashboard.MY_AWARDS.holds(null)).isFalse();
    }

    private void assertRejected(DashboardPreferences request, String field) {
        assertThatThrownBy(() -> service.update(5L, request))
            .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                assertThat(problem.toProblem().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
                assertThat(problem.toProblem().getType().toString()).endsWith("invalid-parameter");
                assertThat(problem.toProblem().getProperties()).containsEntry("parameter", field);
            });
        verify(queries, never()).save(anyLong(), any());
    }
}
