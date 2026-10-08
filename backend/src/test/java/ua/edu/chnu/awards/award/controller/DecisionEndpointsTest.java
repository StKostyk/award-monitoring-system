package ua.edu.chnu.awards.award.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ua.edu.chnu.awards.award.dto.DecisionOutcome;
import ua.edu.chnu.awards.award.dto.ReviewDecisionRequest;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.service.ReviewAssignment;
import ua.edu.chnu.awards.award.service.ReviewDecisions;
import ua.edu.chnu.awards.award.service.ReviewQueue;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;

@WebMvcTest(ReviewController.class)
class DecisionEndpointsTest extends AbstractAwardEndpointsTest {

    private static final String DECISIONS = "/api/v1/awards/5/decisions";
    private static final String PROBLEM = "urn:awards:problem:";

    @MockitoBean
    private ReviewQueue queue;

    @MockitoBean
    private ReviewAssignment assignment;

    @MockitoBean
    private ReviewDecisions decisions;

    @Test
    void ac2_1_aDecisionAnswersTheOutcome() throws Exception {
        when(decisions.decide(5L, new ReviewDecisionRequest(ReviewDecisionRequest.Decision.APPROVE, 3L, null, true)))
            .thenReturn(new DecisionOutcome(5L, AwardStatus.APPROVED, RequestStatus.APPROVED,
                ApprovalLevel.FACULTY_SECRETARY, 4L));

        mockMvc.perform(post(DECISIONS).with(secretary()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\",\"requestVersion\":3,\"verified\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.requestStatus").value("APPROVED"))
            .andExpect(jsonPath("$.requestVersion").value(4));
    }

    @Test
    void ac2_8_anUnknownDecisionAnswers400() throws Exception {
        mockMvc.perform(post(DECISIONS).with(secretary()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"POSTPONE\",\"requestVersion\":3}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void ac2_8_aCallerWithoutAnApprovalRoleGets403() throws Exception {
        mockMvc.perform(post(DECISIONS).with(employee()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\",\"requestVersion\":3}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void ac2_3_aRejectionWithoutCommentAnswers422() throws Exception {
        when(decisions.decide(eq(5L), any())).thenThrow(ApiProblemException.validationFailed("The decision cannot be "
            + "applied", List.of(new FieldViolation("comment", "required", "A comment is required"))));

        mockMvc.perform(post(DECISIONS).with(secretary()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECT\",\"requestVersion\":3}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.type").value(PROBLEM + "validation-failed"));
    }

}
