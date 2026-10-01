package ua.edu.chnu.awards.award.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ua.edu.chnu.awards.award.dto.AwardStatusView;
import ua.edu.chnu.awards.award.dto.DecisionView;
import ua.edu.chnu.awards.award.dto.DelayReason;
import ua.edu.chnu.awards.award.dto.PathStep;
import ua.edu.chnu.awards.award.dto.StatusDelay;
import ua.edu.chnu.awards.award.dto.StepState;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.entity.ReviewDecisionType;
import ua.edu.chnu.awards.award.service.AwardNotFoundException;
import ua.edu.chnu.awards.award.service.AwardStatusService;

@WebMvcTest(AwardController.class)
class AwardStatusEndpointsTest extends AbstractAwardEndpointsTest {

    private static final String STATUS = "/api/v1/awards/5/status";

    @MockitoBean
    private AwardStatusService statusService;

    @Test
    void ac1_6_theStatusAnswersThePathEstimateDelayAndDecisions() throws Exception {
        Instant deadline = Instant.parse("2026-10-01T09:00:00Z");
        when(statusService.status(5L)).thenReturn(new AwardStatusView(5L, AwardStatus.PENDING,
            RequestStatus.RETURNED, ApprovalLevel.DEAN, Instant.parse("2026-09-28T09:00:00Z"), deadline,
            LocalDate.of(2026, 10, 7), true, null, null, new StatusDelay(DelayReason.REVIEW_OVERDUE, deadline),
            List.of(new PathStep(ApprovalLevel.FACULTY_SECRETARY, StepState.DONE, null,
                    Instant.parse("2026-09-29T10:00:00Z")),
                new PathStep(ApprovalLevel.DEAN, StepState.CURRENT, LocalDate.of(2026, 10, 1), null)),
            List.of(new DecisionView(7L, ReviewDecisionType.RETURNED, ApprovalLevel.DEAN, 30L, "Петро Мартинюк",
                "Додайте номер наказу", Instant.parse("2026-09-30T10:00:00Z")))));

        mockMvc.perform(get(STATUS).with(employee()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.awardId").value(5))
            .andExpect(jsonPath("$.requestStatus").value("RETURNED"))
            .andExpect(jsonPath("$.estimatedCompletion").value("2026-10-07"))
            .andExpect(jsonPath("$.overdue").value(true))
            .andExpect(jsonPath("$.delay.reason").value("REVIEW_OVERDUE"))
            .andExpect(jsonPath("$.delay.since").value("2026-10-01T09:00:00Z"))
            .andExpect(jsonPath("$.path[0].state").value("DONE"))
            .andExpect(jsonPath("$.path[1].dueDate").value("2026-10-01"))
            .andExpect(jsonPath("$.decisions[0].reviewerName").value("Петро Мартинюк"))
            .andExpect(jsonPath("$.decisions[0].comments").value("Додайте номер наказу"));
    }

    @Test
    void ac1_7_aDraftAnswersItsStatusWithoutARequest() throws Exception {
        when(statusService.status(5L)).thenReturn(AwardStatusView.withoutRequest(5L, AwardStatus.DRAFT));

        mockMvc.perform(get(STATUS).with(employee()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.requestStatus").doesNotExist())
            .andExpect(jsonPath("$.path").isEmpty())
            .andExpect(jsonPath("$.decisions").isEmpty());
    }

    @Test
    void ac1_8_anUnknownOrHiddenAwardAnswers404AndANonNumericId400() throws Exception {
        when(statusService.status(5L)).thenThrow(new AwardNotFoundException(5L));

        mockMvc.perform(get(STATUS).with(administrator())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/awards/abc/status").with(employee())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("urn:awards:problem:invalid-parameter"));
        mockMvc.perform(get(STATUS)).andExpect(status().isUnauthorized());
    }
}
