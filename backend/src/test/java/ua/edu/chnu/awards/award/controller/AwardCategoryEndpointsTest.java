package ua.edu.chnu.awards.award.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ua.edu.chnu.awards.award.dto.CategorySuggestion;
import ua.edu.chnu.awards.award.dto.SuggestionReason;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.award.service.CategoryCatalogue;
import ua.edu.chnu.awards.award.service.CategorySuggester;

@WebMvcTest(AwardCategoryController.class)
class AwardCategoryEndpointsTest extends AbstractAwardEndpointsTest {

    private static final String SUGGESTIONS = "/api/v1/award-categories/suggestions";

    @MockitoBean
    private CategoryCatalogue catalogue;

    @MockitoBean
    private CategorySuggester suggester;

    @Test
    void ac3_1_anEmployeeGetsRankedSuggestionsWithTheirReasons() throws Exception {
        when(suggester.suggest("Best paper award", "IEEE International Conference")).thenReturn(List.of(
            new CategorySuggestion(3L, "International Conference Best Paper", "Найкраща стаття міжнародної конференції",
                RecognitionLevel.INTERNATIONAL, 100, List.of(SuggestionReason.KEYWORD)),
            new CategorySuggestion(1L, "International Awards", "Міжнародні нагороди", RecognitionLevel.INTERNATIONAL,
                40, List.of(SuggestionReason.KEYWORD, SuggestionReason.HISTORY))));

        mockMvc.perform(get(SUGGESTIONS).param("title", "Best paper award")
                .param("organization", "IEEE International Conference").with(employee()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(3))
            .andExpect(jsonPath("$[0].level").value("INTERNATIONAL"))
            .andExpect(jsonPath("$[0].score").value(100))
            .andExpect(jsonPath("$[0].reasons[0]").value("KEYWORD"))
            .andExpect(jsonPath("$[1].reasons[1]").value("HISTORY"));
    }

    @Test
    void ac3_1_bothParametersAreOptional() throws Exception {
        when(suggester.suggest(null, null)).thenReturn(List.of());

        mockMvc.perform(get(SUGGESTIONS).with(employee()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void ac3_1_withoutAwardCreateSuggestionsAreRefused() throws Exception {
        mockMvc.perform(get(SUGGESTIONS).param("title", "Best paper").with(administrator()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.type").value("urn:awards:problem:access-denied"));
        mockMvc.perform(get(SUGGESTIONS).param("title", "Best paper")).andExpect(status().isUnauthorized());
        verify(suggester, never()).suggest(any(), any());
    }
}
