package ua.edu.chnu.awards.award.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.award.dto.AwardForm;
import ua.edu.chnu.awards.award.dto.AwardQuery;
import ua.edu.chnu.awards.award.dto.DuplicateMatch;
import ua.edu.chnu.awards.award.dto.SubmitRequest;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.service.AwardNotFoundException;
import ua.edu.chnu.awards.award.service.AwardStatusService;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;

@WebMvcTest(AwardController.class)
@MockitoBean(types = AwardStatusService.class)
class AwardEndpointsTest extends AbstractAwardEndpointsTest {

    private static final String AWARDS = "/api/v1/awards";
    private static final String AWARD = "/api/v1/awards/5";

    @Test
    void ac1_1_creatingADraftAnswers201WithItsLocation() throws Exception {
        when(awardService.create(any())).thenReturn(award(AwardStatus.DRAFT));

        mockMvc.perform(post(AWARDS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"titleUk\":\"Грамота МОН\",\"awardDate\":\"2025-05-01\"}")
                .with(employee()))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/awards/5"))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.category.level").value("NATIONAL"))
            .andExpect(jsonPath("$.request").doesNotExist())
            .andExpect(jsonPath("$.recipient.type").value("PERSON"))
            .andExpect(jsonPath("$.version").value(3));
        verify(awardService).create(new AwardForm(null, "Грамота МОН", null, null, null, null,
            LocalDate.of(2025, 5, 1), null, null, null));
    }

    @Test
    void ac1_1_invalidFieldsAnswer422WithOneEntryPerField() throws Exception {
        when(awardService.create(any())).thenThrow(new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY,
            "validation-failed", "The award form has invalid fields", Map.of("errors", List.of(
                new FieldViolation("title", "required", "A title is required"),
                new FieldViolation("externalUrl", "invalid", "http or https")))));

        mockMvc.perform(post(AWARDS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"externalUrl\":\"javascript:alert(1)\"}").with(employee()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.type").value("urn:awards:problem:validation-failed"))
            .andExpect(jsonPath("$.errors.length()").value(2))
            .andExpect(jsonPath("$.errors[1].field").value("externalUrl"))
            .andExpect(jsonPath("$.errors[1].code").value("invalid"));
    }

    @Test
    void ac1_1_anUnreadableValueAnswers422NamingTheField() throws Exception {
        mockMvc.perform(post(AWARDS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"titleUk\":\"x\",\"awardDate\":\"2026-02-30\"}").with(employee()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.type").value("urn:awards:problem:validation-failed"))
            .andExpect(jsonPath("$.errors[0].field").value("awardDate"))
            .andExpect(jsonPath("$.errors[0].code").value("invalid"));
        mockMvc.perform(put(AWARD).contentType(MediaType.APPLICATION_JSON).content("{not json")
                .with(employee()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].field").value("body"));
        verify(awardService, never()).create(any());
    }

    @Test
    void ac1_2_withoutAwardCreateTheRequestIsRefusedAndAudited() throws Exception {
        mockMvc.perform(post(AWARDS).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}")
                .with(administrator()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.type").value("urn:awards:problem:access-denied"))
            .andExpect(jsonPath("$.detail").value("permission award:create is required"));
        mockMvc.perform(post(AWARD + "/submit").contentType(MediaType.APPLICATION_JSON).content("{\"version\":3}")
                .with(administrator()))
            .andExpect(status().isForbidden());
        verify(awardService, never()).create(any());
        verify(audit, times(2)).recordSeparately(eq(AuditAction.ACCESS_DENIED), anyString(), eq(1L), anyMap());
    }

    @Test
    void ac1_2_withoutATokenTheApiAnswers401() throws Exception {
        mockMvc.perform(get(AWARDS)).andExpect(status().isUnauthorized());
    }

    @Test
    void ac1_3_aStaleVersionAnswers409WithTheCurrentOne() throws Exception {
        when(awardService.update(eq(5L), any())).thenThrow(new ApiProblemException(HttpStatus.CONFLICT,
            "award-stale", "The award was changed in the meantime", Map.of("currentVersion", 4L)));

        mockMvc.perform(put(AWARD).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Letter\",\"version\":3}").with(employee()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("urn:awards:problem:award-stale"))
            .andExpect(jsonPath("$.currentVersion").value(4));
    }

    @Test
    void ac1_3_anUpdateAnswersTheNewVersion() throws Exception {
        when(awardService.update(eq(5L), any())).thenReturn(award(AwardStatus.DRAFT));

        mockMvc.perform(put(AWARD).contentType(MediaType.APPLICATION_JSON)
                .content("{\"titleUk\":\"Грамота МОН\",\"version\":2}").with(employee()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void ac1_4_deletingADraftAnswers204() throws Exception {
        mockMvc.perform(delete(AWARD).with(employee())).andExpect(status().isNoContent());

        verify(awardService).delete(5L);
    }

    @Test
    void ac1_5_submissionAnswersTheAwardWithItsRequest() throws Exception {
        when(submission.submit(5L, new SubmitRequest(3L, null))).thenReturn(award(AwardStatus.PENDING));

        mockMvc.perform(post(AWARD + "/submit").contentType(MediaType.APPLICATION_JSON).content("{\"version\":3}")
                .with(employee()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.impactScore").value(80))
            .andExpect(jsonPath("$.request.status").value("SUBMITTED"))
            .andExpect(jsonPath("$.request.currentLevel").value("FACULTY_SECRETARY"));
    }

    @Test
    void ac2_5_aPossibleDuplicateAnswers409WithTheMatches() throws Exception {
        when(submission.submit(5L, new SubmitRequest(3L, false))).thenThrow(new ApiProblemException(
            HttpStatus.CONFLICT, "award-possible-duplicate", "Looks like an award already entered",
            Map.of("matches", List.of(new DuplicateMatch(9L, null, "Грамота МОН", LocalDate.of(2025, 5, 1),
                AwardStatus.PENDING)))));

        mockMvc.perform(post(AWARD + "/submit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":3,\"acknowledgeDuplicate\":false}").with(employee()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("urn:awards:problem:award-possible-duplicate"))
            .andExpect(jsonPath("$.matches[0].id").value(9))
            .andExpect(jsonPath("$.matches[0].awardDate").value("2025-05-01"))
            .andExpect(jsonPath("$.matches[0].status").value("PENDING"));
    }

    @Test
    void ac1_6_aRepeatedSubmissionAnswers409() throws Exception {
        when(submission.submit(eq(5L), any())).thenThrow(new ApiProblemException(HttpStatus.CONFLICT,
            "award-not-editable", "The award was submitted", Map.of("awardStatus", "PENDING")));

        mockMvc.perform(post(AWARD + "/submit").with(employee()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("urn:awards:problem:award-not-editable"))
            .andExpect(jsonPath("$.awardStatus").value("PENDING"));
    }

    @Test
    void ac1_7_theListPassesItsFilters() throws Exception {
        AwardQuery query = new AwardQuery(AwardStatus.PENDING, 13L, LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 12, 31));
        when(awardService.listOwn(query, 1, 50)).thenReturn(new PageImpl<>(List.of(award(AwardStatus.PENDING))));

        mockMvc.perform(get(AWARDS).param("status", "PENDING").param("category", "13")
                .param("dateFrom", "2025-01-01").param("dateTo", "2025-12-31").param("page", "1")
                .param("size", "50").with(employee()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].request.status").value("SUBMITTED"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void ac1_8_anUnknownOrHiddenAwardAnswers404() throws Exception {
        when(awardService.get(5L)).thenThrow(new AwardNotFoundException(5L));

        mockMvc.perform(get(AWARD).with(administrator())).andExpect(status().isNotFound());
        mockMvc.perform(get(AWARDS + "/abc").with(employee())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("urn:awards:problem:invalid-parameter"))
            .andExpect(jsonPath("$.parameter").value("id"));
        mockMvc.perform(get(AWARDS + "/99999999999999999999").with(employee())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("urn:awards:problem:invalid-parameter"));
        verify(awardService, never()).listOwn(any(), anyInt(), anyInt());
        verify(submission, never()).submit(anyLong(), any());
    }
}
