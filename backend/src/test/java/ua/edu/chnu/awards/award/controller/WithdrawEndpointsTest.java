package ua.edu.chnu.awards.award.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ua.edu.chnu.awards.award.dto.WithdrawRequest;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.service.AwardNotFoundException;
import ua.edu.chnu.awards.award.service.AwardWithdrawal;
import ua.edu.chnu.awards.common.web.ApiProblemException;

@WebMvcTest(WithdrawalController.class)
class WithdrawEndpointsTest extends AbstractAwardEndpointsTest {

    private static final String WITHDRAW = "/api/v1/awards/5/withdraw";
    private static final String PROBLEM = "urn:awards:problem:";

    @MockitoBean
    private AwardWithdrawal withdrawal;

    @Test
    void ac3_1_aWithdrawalAnswersTheDraft() throws Exception {
        when(withdrawal.withdraw(5L, new WithdrawRequest(2L))).thenReturn(award(AwardStatus.DRAFT));

        mockMvc.perform(post(WITHDRAW).with(employee()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void ac3_2_aClaimedRequestAnswers409() throws Exception {
        when(withdrawal.withdraw(eq(5L), any())).thenThrow(new ApiProblemException(HttpStatus.CONFLICT,
            "request-claimed", "A reviewer is already reviewing the award", Map.of()));

        mockMvc.perform(post(WITHDRAW).with(employee()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value(PROBLEM + "request-claimed"));
    }

    @Test
    void ac3_2_someoneElsesAwardAnswers404() throws Exception {
        when(withdrawal.withdraw(eq(5L), any())).thenThrow(new AwardNotFoundException(5L));

        mockMvc.perform(post(WITHDRAW).with(employee()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void ac3_1_aCallerWithoutTheUpdatePermissionGets403() throws Exception {
        mockMvc.perform(post(WITHDRAW).with(administrator()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(withdrawal);
    }
}
