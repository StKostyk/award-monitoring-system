package ua.edu.chnu.awards.award.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import ua.edu.chnu.awards.audit.dto.AuditTrailEntry;
import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.award.dto.AwardVersionResponse;
import ua.edu.chnu.awards.award.dto.FieldChange;
import ua.edu.chnu.awards.award.dto.UserRef;
import ua.edu.chnu.awards.award.entity.AwardSnapshot;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.VersionAction;
import ua.edu.chnu.awards.award.service.AwardNotFoundException;

@WebMvcTest(AwardHistoryController.class)
class AwardHistoryEndpointsTest extends AbstractAwardEndpointsTest {

    private static final String VERSIONS = "/api/v1/awards/5/versions";
    private static final String TRAIL = "/api/v1/awards/5/audit-trail";

    @Test
    void ac1_8_versionsAnswerThePageWithSnapshotsAndChanges() throws Exception {
        AwardSnapshot snapshot = new AwardSnapshot("Letter", null, null, null, "МОН", LocalDate.of(2025, 5, 1),
            13L, AwardStatus.DRAFT, null, false, null, 64L);
        AwardVersionResponse version = new AwardVersionResponse(2L, VersionAction.UPDATED,
            new UserRef(21L, "Анастасія Коваль", "employee.fmi@chnu.edu.ua"), Instant.parse("2026-09-30T08:00:00Z"),
            snapshot, List.of(new FieldChange("categoryId", null, 13L)));
        when(history.versions(5L, 0, 20)).thenReturn(new PageImpl<>(List.of(version)));

        mockMvc.perform(get(VERSIONS).with(employee()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].number").value(2))
            .andExpect(jsonPath("$.content[0].action").value("UPDATED"))
            .andExpect(jsonPath("$.content[0].actor.name").value("Анастасія Коваль"))
            .andExpect(jsonPath("$.content[0].snapshot.awardDate").value("2025-05-01"))
            .andExpect(jsonPath("$.content[0].changes[0].field").value("categoryId"))
            .andExpect(jsonPath("$.content[0].changes[0].to").value(13))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void ac1_9_versionsOfAHiddenOrUnknownAwardAnswer404AndABadIdAnswers400() throws Exception {
        when(history.versions(5L, 1, 50)).thenThrow(new AwardNotFoundException(5L));

        mockMvc.perform(get(VERSIONS).param("page", "1").param("size", "50").with(administrator()))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/awards/abc/versions").with(employee()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("urn:awards:problem:invalid-parameter"));
    }

    @Test
    void ac1_10_theAuditTrailAnswersItsRowsToAnAuditor() throws Exception {
        UUID correlation = UUID.randomUUID();
        AuditTrailEntry row = new AuditTrailEntry(90L, Instant.parse("2026-09-30T08:00:00Z"), 21L, "Анастасія Коваль",
            "employee.fmi@chnu.edu.ua", "AWARD_SUBMITTED", "awards", 5L, List.of(), null,
            Map.of("requestId", 40), "10.0.0.1", correlation);
        when(auditTrail.aboutAward(5L, 0, 20)).thenReturn(new PageImpl<>(List.of(row)));

        mockMvc.perform(get(TRAIL).with(auditor()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].action").value("AWARD_SUBMITTED"))
            .andExpect(jsonPath("$.content[0].actorEmail").value("employee.fmi@chnu.edu.ua"))
            .andExpect(jsonPath("$.content[0].newValues.requestId").value(40))
            .andExpect(jsonPath("$.content[0].ipAddress").value("10.0.0.1"))
            .andExpect(jsonPath("$.content[0].correlationId").value(correlation.toString()));
    }

    @Test
    void ac1_10_anAwardNobodyLoggedAnswers404() throws Exception {
        when(auditTrail.aboutAward(5L, 0, 20)).thenReturn(Page.empty());

        mockMvc.perform(get(TRAIL).with(auditor()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Not found"));
    }

    @Test
    void ac1_11_withoutAuditReadTheTrailIsRefusedAndAudited() throws Exception {
        mockMvc.perform(get(TRAIL).with(administrator()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.type").value("urn:awards:problem:access-denied"))
            .andExpect(jsonPath("$.detail").value("permission audit:read is required"));
        verify(auditTrail, never()).aboutAward(anyLong(), anyInt(), anyInt());
        verify(audit).recordSeparately(eq(AuditAction.ACCESS_DENIED), anyString(), eq(1L), anyMap());
    }

    private static RequestPostProcessor auditor() {
        return jwt().jwt(token -> token.subject("2").claim("role_scopes", List.of("GDPR_OFFICER:1")))
            .authorities(new SimpleGrantedAuthority("award:read:own"), new SimpleGrantedAuthority("audit:read"));
    }
}
