package ua.edu.chnu.awards.award.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ua.edu.chnu.awards.award.dto.BatchItemResult;
import ua.edu.chnu.awards.award.dto.DecisionOutcome;
import ua.edu.chnu.awards.award.dto.ReviewDecisionRequest.Decision;
import ua.edu.chnu.awards.award.dto.ReviewTemplateView;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.service.BatchReview;
import ua.edu.chnu.awards.award.service.ReviewTemplates;
import ua.edu.chnu.awards.common.web.ApiProblemException;

@WebMvcTest(ReviewBatchController.class)
class BatchEndpointsTest extends AbstractAwardEndpointsTest {

    private static final String DECISIONS = "/api/v1/reviews/decisions";
    private static final String BODY = "{\"decision\":\"APPROVE\",\"items\":[{\"awardId\":5,\"requestVersion\":3},"
        + "{\"awardId\":6,\"requestVersion\":1}]}";

    @MockitoBean
    private BatchReview batch;

    @MockitoBean
    private ReviewTemplates templates;

    @Test
    void ac4_1_aBatchAnswersOneResultPerItemInOrder() throws Exception {
        when(batch.decide(any())).thenReturn(List.of(
            BatchItemResult.done(new DecisionOutcome(5L, AwardStatus.APPROVED, RequestStatus.APPROVED,
                ApprovalLevel.FACULTY_SECRETARY, 4L)),
            BatchItemResult.failed(6L, "request-claimed", "Claimed by another reviewer")));

        mockMvc.perform(post(DECISIONS).with(secretary()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].awardId").value(5))
            .andExpect(jsonPath("$[0].outcome").value("DONE"))
            .andExpect(jsonPath("$[0].status").value("APPROVED"))
            .andExpect(jsonPath("$[0].code").doesNotExist())
            .andExpect(jsonPath("$[1].outcome").value("FAILED"))
            .andExpect(jsonPath("$[1].code").value("request-claimed"))
            .andExpect(jsonPath("$[1].status").doesNotExist());
    }

    @Test
    void ac4_2_anInvalidItemListAnswers400() throws Exception {
        when(batch.decide(any())).thenThrow(new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-parameter",
            "A batch holds 1 to 50 items", Map.of("parameter", "items")));

        mockMvc.perform(post(DECISIONS).with(secretary()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\",\"items\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("urn:awards:problem:invalid-parameter"))
            .andExpect(jsonPath("$.parameter").value("items"));
    }

    @Test
    void ac4_2_aCallerWithoutAnApprovalRoleGets403() throws Exception {
        mockMvc.perform(post(DECISIONS).with(employee()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void ac4_5_templatesAnswerTheListOfADecision() throws Exception {
        when(templates.of(Decision.RETURN)).thenReturn(List.of(
            new ReviewTemplateView(1L, Decision.RETURN, "Немає скану", "Додайте скан сертифіката")));

        mockMvc.perform(get("/api/v1/reviews/templates").param("decision", "RETURN").with(secretary()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].decision").value("RETURN"))
            .andExpect(jsonPath("$[0].body").value("Додайте скан сертифіката"));
    }

    @Test
    void ac4_5_templatesNeedAnApprovalRole() throws Exception {
        mockMvc.perform(get("/api/v1/reviews/templates").param("decision", "RETURN").with(employee()))
            .andExpect(status().isForbidden());
    }

    @Test
    void ac4_5_anUnknownDecisionAnswers400() throws Exception {
        mockMvc.perform(get("/api/v1/reviews/templates").param("decision", "POSTPONE").with(secretary()))
            .andExpect(status().isBadRequest());
    }
}
