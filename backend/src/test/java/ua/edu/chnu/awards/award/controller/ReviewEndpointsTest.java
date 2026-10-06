package ua.edu.chnu.awards.award.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import ua.edu.chnu.awards.award.dto.AwardRecipient;
import ua.edu.chnu.awards.award.dto.ReviewItem;
import ua.edu.chnu.awards.award.dto.ReviewQuery;
import ua.edu.chnu.awards.award.dto.ReviewerCandidate;
import ua.edu.chnu.awards.award.dto.ReviewerChange;
import ua.edu.chnu.awards.award.dto.UserRef;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.service.AwardNotFoundException;
import ua.edu.chnu.awards.award.service.ReviewAssignment;
import ua.edu.chnu.awards.award.service.ReviewDecisions;
import ua.edu.chnu.awards.award.service.ReviewQueue;
import ua.edu.chnu.awards.common.web.ApiProblemException;

@WebMvcTest(ReviewController.class)
class ReviewEndpointsTest extends AbstractAwardEndpointsTest {

    private static final String REVIEWS = "/api/v1/reviews";
    private static final String REVIEWER = "/api/v1/awards/5/reviewer";
    private static final String PROBLEM = "urn:awards:problem:";

    @MockitoBean
    private ReviewQueue queue;

    @MockitoBean
    private ReviewAssignment assignment;

    @MockitoBean
    private ReviewDecisions decisions;

    @Test
    void ac1_1_theQueueAnswersAPageOfReviewItems() throws Exception {
        when(queue.list(any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of(item()), PageRequest.of(0, 20),
            1));

        mockMvc.perform(get(REVIEWS).param("assigned", "unassigned").param("level", "FACULTY_SECRETARY")
                .param("organizationId", "64").param("overdue", "true").with(secretary()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].awardId").value(5))
            .andExpect(jsonPath("$.content[0].requestVersion").value(3))
            .andExpect(jsonPath("$.content[0].level").value("FACULTY_SECRETARY"))
            .andExpect(jsonPath("$.content[0].reviewer").isEmpty())
            .andExpect(jsonPath("$.totalElements").value(1));
        verify(queue).list(new ReviewQuery(ReviewQuery.Assignment.UNASSIGNED, ApprovalLevel.FACULTY_SECRETARY, 64L,
            true), 0, 20);
    }

    @Test
    void ac1_2_anUnknownFilterValueAnswers400() throws Exception {
        mockMvc.perform(get(REVIEWS).param("assigned", "nobody").with(secretary()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value(PROBLEM + "invalid-parameter"))
            .andExpect(jsonPath("$.parameter").value("assigned"));
        mockMvc.perform(get(REVIEWS).param("level", "PROVOST").with(secretary()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.parameter").value("level"));
    }

    @Test
    void ac1_3_aCallerWithoutAnApprovalRoleGets403() throws Exception {
        mockMvc.perform(get(REVIEWS).with(employee())).andExpect(status().isForbidden());
    }

    @Test
    void ac1_4_aClaimAnswersTheItem() throws Exception {
        when(assignment.assign(5L, new ReviewerChange(3L, null, null))).thenReturn(item());

        mockMvc.perform(put(REVIEWER).with(secretary()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestVersion\":3}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestId").value(8));
    }

    @Test
    void ac1_5_aClaimHeldByAColleagueAnswers409WithTheReviewer() throws Exception {
        when(assignment.assign(eq(5L), any())).thenThrow(new ApiProblemException(HttpStatus.CONFLICT,
            "request-claimed", "Another reviewer holds the request",
            Map.of("reviewer", new UserRef(6L, "Олена Петрук", "secretary2.fmi@chnu.edu.ua"))));

        mockMvc.perform(put(REVIEWER).with(secretary()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestVersion\":3,\"takeOver\":true}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value(PROBLEM + "request-claimed"))
            .andExpect(jsonPath("$.reviewer.name").value("Олена Петрук"));
    }

    @Test
    void ac1_9_aRequestTheCallerMayNotReviewAnswers404() throws Exception {
        when(assignment.candidates(5L)).thenThrow(new AwardNotFoundException(5L));
        when(assignment.item(5L)).thenThrow(new AwardNotFoundException(5L));

        mockMvc.perform(get("/api/v1/awards/5/reviewers").with(secretary())).andExpect(status().isNotFound());
        mockMvc.perform(get(REVIEWER).with(secretary())).andExpect(status().isNotFound());
    }

    @Test
    void ac1_11_theReviewerResourceAnswersTheItem() throws Exception {
        when(assignment.item(5L)).thenReturn(item());

        mockMvc.perform(get(REVIEWER).with(secretary()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestVersion").value(3));
        mockMvc.perform(get(REVIEWER).with(employee())).andExpect(status().isForbidden());
    }

    @Test
    void ac1_7_releaseAnswers204AndNeedsTheVersion() throws Exception {
        mockMvc.perform(delete(REVIEWER).param("requestVersion", "3").with(secretary()))
            .andExpect(status().isNoContent());
        verify(assignment).release(5L, 3L);

        mockMvc.perform(delete(REVIEWER).with(secretary()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value(PROBLEM + "missing-parameter"));
    }

    @Test
    void ac1_8_theCandidatesAnswerWithTheDelegatedFlag() throws Exception {
        when(assignment.candidates(5L)).thenReturn(List.of(new ReviewerCandidate(6L, "Олена Петрук",
            "secretary2.fmi@chnu.edu.ua", true)));

        mockMvc.perform(get("/api/v1/awards/5/reviewers").with(secretary()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(6))
            .andExpect(jsonPath("$[0].delegated").value(true));
    }

    private static RequestPostProcessor secretary() {
        return jwt().jwt(token -> token.subject("31").claim("role_scopes", List.of("FACULTY_SECRETARY:9")))
            .authorities(new SimpleGrantedAuthority("award:read:own"),
                new SimpleGrantedAuthority("award:approve:level1"));
    }

    private static ReviewItem item() {
        return new ReviewItem(5L, 8L, 3L, "Certificate", "Грамота", AwardRecipient.PERSON,
            new UserRef(21L, "Анастасія Коваль", "employee.fmi@chnu.edu.ua"), null, null,
            ApprovalLevel.FACULTY_SECRETARY, RequestStatus.SUBMITTED, null, Instant.parse("2026-10-01T08:00:00Z"),
            Instant.parse("2026-10-06T21:00:00Z"), false, 2, null);
    }
}
