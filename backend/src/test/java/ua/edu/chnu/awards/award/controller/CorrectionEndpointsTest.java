package ua.edu.chnu.awards.award.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ua.edu.chnu.awards.award.dto.AwardCorrectionRequest;
import ua.edu.chnu.awards.award.dto.CorrectionOutcome;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.service.AwardCorrection;
import ua.edu.chnu.awards.award.service.AwardNotFoundException;
import ua.edu.chnu.awards.common.web.ApiProblemException;

@WebMvcTest(CorrectionController.class)
class CorrectionEndpointsTest extends AbstractAwardEndpointsTest {

    private static final String CORRECT = "/api/v1/awards/5/corrections";
    private static final String PROBLEM = "urn:awards:problem:";
    private static final String BODY = "{\"titleUk\":\"Нова назва\",\"externalUrl\":null,\"version\":2,"
        + "\"requestVersion\":3,\"reason\":\"Назва з наказу\"}";

    @MockitoBean
    private AwardCorrection correction;

    @Test
    void ac3_3_aCorrectionAnswersTheAwardTheRequestVersionAndTheChangedFields() throws Exception {
        when(correction.correct(eq(5L), any())).thenReturn(new CorrectionOutcome(award(AwardStatus.PENDING), 4L,
            List.of("titleUk", "externalUrl")));

        mockMvc.perform(post(CORRECT).with(secretary()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.award.status").value("PENDING"))
            .andExpect(jsonPath("$.requestVersion").value(4))
            .andExpect(jsonPath("$.changedFields[1]").value("externalUrl"));
    }

    @Test
    void ac3_3_anAbsentFieldStaysUnchangedAndANullOneIsCleared() throws Exception {
        ArgumentCaptor<AwardCorrectionRequest> sent = ArgumentCaptor.forClass(AwardCorrectionRequest.class);
        when(correction.correct(eq(5L), sent.capture())).thenReturn(new CorrectionOutcome(
            award(AwardStatus.PENDING), 4L, List.of("titleUk")));

        mockMvc.perform(post(CORRECT).with(secretary()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk());

        AwardCorrectionRequest body = sent.getValue();
        assertThat(body.getTitleUk()).contains("Нова назва");
        assertThat(body.getExternalUrl()).isEmpty();
        assertThat(body.getTitle()).isNull();
        assertThat(body.getAwardDate()).isNull();
        assertThat(body.getVersion()).isEqualTo(2L);
        assertThat(body.getRequestVersion()).isEqualTo(3L);
        assertThat(body.getReason()).isEqualTo("Назва з наказу");
    }

    @Test
    void ac3_7_problemsOfTheServiceAnswerTheirStatus() throws Exception {
        when(correction.correct(eq(5L), any()))
            .thenThrow(new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "no-change", "Nothing", Map.of()))
            .thenThrow(new AwardNotFoundException(5L));

        mockMvc.perform(post(CORRECT).with(secretary()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.type").value(PROBLEM + "no-change"));
        mockMvc.perform(post(CORRECT).with(secretary()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isNotFound());
        verify(correction, times(2)).correct(eq(5L), any());
    }

    @Test
    void ac3_7_aCallerWithoutTheReviewPermissionGets403() throws Exception {
        mockMvc.perform(post(CORRECT).with(employee()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
        verifyNoInteractions(correction);
    }

    @Test
    void ac3_7_aMalformedFieldAnswers400() throws Exception {
        mockMvc.perform(post(CORRECT).with(secretary()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"awardDate\":\"yesterday\",\"version\":2,\"requestVersion\":3,\"reason\":\"x\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(correction);
    }
}
