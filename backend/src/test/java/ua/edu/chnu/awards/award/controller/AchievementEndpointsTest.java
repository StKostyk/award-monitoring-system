package ua.edu.chnu.awards.award.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ua.edu.chnu.awards.award.dto.Achievement;
import ua.edu.chnu.awards.award.dto.AchievementDetail;
import ua.edu.chnu.awards.award.dto.AchievementQuery;
import ua.edu.chnu.awards.award.dto.AchievementRecipient;
import ua.edu.chnu.awards.award.dto.CategoryRef;
import ua.edu.chnu.awards.award.dto.RecipientType;
import ua.edu.chnu.awards.award.dto.SharedDocument;
import ua.edu.chnu.awards.award.dto.UnitRef;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.award.service.Achievements;
import ua.edu.chnu.awards.award.service.UnitNotFoundException;
import ua.edu.chnu.awards.organization.entity.OrganizationType;

@WebMvcTest(AchievementController.class)
class AchievementEndpointsTest extends AbstractAwardEndpointsTest {

    private static final String ACHIEVEMENTS = "/api/v1/achievements";

    @MockitoBean
    private Achievements achievements;

    @Test
    void ac1_5_aSignedInUserGetsTheSharedProjectionUncached() throws Exception {
        Achievement achievement = new Achievement(5L, null, "Грамота МОН", null, null,
            new CategoryRef(13L, "Ministry Recognition", "Відзнака міністерства", RecognitionLevel.NATIONAL),
            "МОН України", LocalDate.of(2025, 5, 1), null, true, new AchievementRecipient(RecipientType.PERSON,
            "Анастасія Коваль", new UnitRef(64L, "Algebra", "Кафедра алгебри", OrganizationType.DEPARTMENT)));
        when(achievements.shared(new AchievementQuery(10L, 2025, RecognitionLevel.NATIONAL, RecipientType.PERSON),
            0, 20)).thenReturn(new PageImpl<>(List.of(achievement)));

        mockMvc.perform(get(ACHIEVEMENTS).with(secretary()).param("unit", "10").param("year", "2025")
                .param("level", "NATIONAL").param("recipient", "PERSON"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.content[0].awardId").value(5))
            .andExpect(jsonPath("$.content[0].recipient.personName").value("Анастасія Коваль"))
            .andExpect(jsonPath("$.content[0].owner").doesNotExist())
            .andExpect(jsonPath("$.content[0].impactScore").doesNotExist());
    }

    @Test
    void ac1_2_theSearchTextIsPassedWithTheFilters() throws Exception {
        when(achievements.shared(new AchievementQuery(null, 2025, null, null, null, "horizon"), 0, 20))
            .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(ACHIEVEMENTS).with(secretary()).param("q", "horizon").param("year", "2025"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.content").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"year=abc", "level=GALACTIC", "recipient=TEAM", "unit=x"})
    void ac1_6_aMalformedFilterIsAnInvalidParameter(String filter) throws Exception {
        mockMvc.perform(get(ACHIEVEMENTS + "?" + filter).with(employee()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("urn:awards:problem:invalid-parameter"));
        verifyNoInteractions(achievements);
    }

    @Test
    void ac1_6_anUnknownUnitAnswers404() throws Exception {
        when(achievements.shared(new AchievementQuery(99L, null, null, null), 0, 20))
            .thenThrow(new UnitNotFoundException(99L));

        mockMvc.perform(get(ACHIEVEMENTS).with(employee()).param("unit", "99"))
            .andExpect(status().isNotFound());
    }

    @Test
    void ac2_aSignedInUserGetsOneSharedAwardWithItsShownDocumentsUncached() throws Exception {
        Achievement achievement = new Achievement(5L, null, "Грамота МОН", null, null,
            new CategoryRef(13L, "Ministry Recognition", "Відзнака міністерства", RecognitionLevel.NATIONAL),
            "МОН України", LocalDate.of(2025, 5, 1), null, true, new AchievementRecipient(RecipientType.PERSON,
            "Анастасія Коваль", new UnitRef(64L, "Algebra", "Кафедра алгебри", OrganizationType.DEPARTMENT)));
        when(achievements.detail(5L)).thenReturn(new AchievementDetail(achievement, List.of(
            new SharedDocument(7L, "диплом.pdf", "DIPLOMA", "application/pdf", 3L, null))));

        mockMvc.perform(get(ACHIEVEMENTS + "/5").with(employee()))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.achievement.awardId").value(5))
            .andExpect(jsonPath("$.documents[0].id").value(7))
            .andExpect(jsonPath("$.documents[0].storageKey").doesNotExist());
        mockMvc.perform(get(ACHIEVEMENTS + "/5")).andExpect(status().isUnauthorized());
    }

    @Test
    void ac1_5_anAnonymousCallerGets401() throws Exception {
        mockMvc.perform(get(ACHIEVEMENTS))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(achievements);
    }
}
