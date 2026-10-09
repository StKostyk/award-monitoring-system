package ua.edu.chnu.awards.award.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import ua.edu.chnu.awards.award.dto.AchievementQuery;
import ua.edu.chnu.awards.award.dto.AchievementRecipient;
import ua.edu.chnu.awards.award.dto.CategoryRef;
import ua.edu.chnu.awards.award.dto.RecipientType;
import ua.edu.chnu.awards.award.dto.UnitRef;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.award.service.Achievements;
import ua.edu.chnu.awards.award.service.UnitNotFoundException;
import ua.edu.chnu.awards.user.entity.OrganizationType;

@WebMvcTest({PublicAchievementController.class, AchievementController.class})
class PublicAchievementEndpointsTest extends AbstractAwardEndpointsTest {

    private static final String PUBLIC_ACHIEVEMENTS = "/api/v1/public/achievements";

    @MockitoBean
    private Achievements achievements;

    @Test
    void ac2_1_anAnonymousVisitorGetsThePublishedProjectionUncached() throws Exception {
        Achievement achievement = new Achievement(5L, null, "Грамота МОН", null, null,
            new CategoryRef(13L, "Ministry Recognition", "Відзнака міністерства", RecognitionLevel.NATIONAL),
            "МОН України", LocalDate.of(2025, 5, 1), null, true, new AchievementRecipient(RecipientType.PERSON,
            "Анастасія Коваль", new UnitRef(64L, "Algebra", "Кафедра алгебри", OrganizationType.DEPARTMENT)));
        when(achievements.published(new AchievementQuery(10L, 2025, RecognitionLevel.NATIONAL,
            RecipientType.PERSON), 0, 20)).thenReturn(new PageImpl<>(List.of(achievement)));

        mockMvc.perform(get(PUBLIC_ACHIEVEMENTS).param("unit", "10").param("year", "2025")
                .param("level", "NATIONAL").param("recipient", "PERSON"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.content[0].awardId").value(5))
            .andExpect(jsonPath("$.content[0].owner").doesNotExist())
            .andExpect(jsonPath("$.content[0].impactScore").doesNotExist());
    }

    @Test
    void ac2_2_aMalformedBearerTokenIsIgnored() throws Exception {
        when(achievements.published(new AchievementQuery(null, null, null, null), 0, 20))
            .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(PUBLIC_ACHIEVEMENTS).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"))
            .andExpect(status().isOk());
    }

    @Test
    void ac2_2_theSignedInListStillNeedsAToken() throws Exception {
        mockMvc.perform(get("/api/v1/achievements"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(achievements);
    }

    @Test
    void ac2_2_onlyReadsArePublic() throws Exception {
        mockMvc.perform(post(PUBLIC_ACHIEVEMENTS))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(achievements);
    }

    @ParameterizedTest
    @ValueSource(strings = {"year=abc", "level=GALACTIC", "recipient=TEAM", "unit=x"})
    void ac2_1_aMalformedFilterIsAnInvalidParameter(String filter) throws Exception {
        mockMvc.perform(get(PUBLIC_ACHIEVEMENTS + "?" + filter))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("urn:awards:problem:invalid-parameter"));
        verifyNoInteractions(achievements);
    }

    @Test
    void ac2_1_anUnknownUnitAnswers404() throws Exception {
        when(achievements.published(new AchievementQuery(99L, null, null, null), 0, 20))
            .thenThrow(new UnitNotFoundException(99L));

        mockMvc.perform(get(PUBLIC_ACHIEVEMENTS).param("unit", "99"))
            .andExpect(status().isNotFound());
    }
}
