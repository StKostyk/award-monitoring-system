package ua.edu.chnu.awards.delegation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.delegation.dto.DelegationListResponse;
import ua.edu.chnu.awards.delegation.dto.DelegationResponse;
import ua.edu.chnu.awards.delegation.dto.UserBrief;
import ua.edu.chnu.awards.delegation.entity.DelegationState;
import ua.edu.chnu.awards.delegation.service.DelegationNotFoundException;
import ua.edu.chnu.awards.user.dto.OrganizationRef;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;

@WebMvcTest(DelegationController.class)
class DelegationEndpointsTest extends AbstractDelegationEndpointsTest {

    private static final String DELEGATIONS = "/api/v1/delegations";

    @Test
    void ac3_1_lendingAuthorityAnswers201WithTheDelegation() throws Exception {
        when(delegationService.create(any())).thenReturn(delegation(DelegationState.ACTIVE));

        mockMvc.perform(post(DELEGATIONS).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"delegateId":5,"role":"DEAN","organizationId":9,"validFrom":"2026-09-24",
                     "validTo":"2026-10-08","reason":"Відпустка"}
                    """)
                .with(approver())
                .with(csrf()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(12))
            .andExpect(jsonPath("$.role").value("DEAN"))
            .andExpect(jsonPath("$.state").value("active"))
            .andExpect(jsonPath("$.delegate.id").value(5));

        verify(delegationService).create(argThat(request -> request.role() == RoleType.DEAN
            && request.delegateId() == 5L && request.organizationId() == 9L
            && request.validTo().equals(LocalDate.of(2026, 10, 8))));
    }

    @Test
    void ac3_1_abodyWithoutAnEndDateIsRejectedBeforeTheService() throws Exception {
        mockMvc.perform(post(DELEGATIONS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"delegateId\":5,\"role\":\"DEAN\",\"organizationId\":9,\"validFrom\":\"2026-09-24\"}")
                .with(approver())
                .with(csrf()))
            .andExpect(status().isBadRequest());

        verify(delegationService, never()).create(any());
    }

    @Test
    void ac3_1_acallerWithoutApprovalAuthorityCannotLendAnything() throws Exception {
        mockMvc.perform(post(DELEGATIONS).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"delegateId":5,"role":"DEAN","organizationId":9,"validFrom":"2026-09-24",
                     "validTo":"2026-10-08"}
                    """)
                .with(jwt().jwt(token -> token.subject("7"))
                    .authorities(new SimpleGrantedAuthority("award:create")))
                .with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.type").value("urn:awards:problem:access-denied"));

        verify(delegationService, never()).create(any());
    }

    @Test
    void ac3_5_thePageIsReadWithoutAStateFilter() throws Exception {
        when(delegationService.list(isNull(), isNull())).thenReturn(new DelegationListResponse(
            List.of(delegation(DelegationState.ACTIVE)), List.of(delegation(DelegationState.EXPIRED))));

        mockMvc.perform(get(DELEGATIONS).with(approver()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.given[0].state").value("active"))
            .andExpect(jsonPath("$.received[0].state").value("expired"));
    }

    @Test
    void ac3_5_theStateFilterIsReadInLowerCase() throws Exception {
        when(delegationService.list(eq(DelegationState.EXPIRED), isNull()))
            .thenReturn(new DelegationListResponse(List.of(), List.of()));

        mockMvc.perform(get(DELEGATIONS).queryParam("state", "expired").with(approver()))
            .andExpect(status().isOk());

        verify(delegationService).list(DelegationState.EXPIRED, null);
    }

    @Test
    void ac3_5_onlyAnAdministratorReadsSomebodyElsesPage() throws Exception {
        mockMvc.perform(get(DELEGATIONS).queryParam("delegatorId", "3").with(approver()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.type").value("urn:awards:problem:access-denied"));

        when(delegationService.list(isNull(), eq(3L)))
            .thenReturn(new DelegationListResponse(List.of(), List.of()));
        mockMvc.perform(get(DELEGATIONS).queryParam("delegatorId", "3")
                .with(jwt().jwt(token -> token.subject("1"))
                    .authorities(new SimpleGrantedAuthority("user:manage"))))
            .andExpect(status().isOk());
    }

    @Test
    void ac3_4_revocationAnswers204() throws Exception {
        mockMvc.perform(delete(DELEGATIONS + "/12").with(approver()).with(csrf()))
            .andExpect(status().isNoContent());

        verify(delegationService).revoke(12L);
    }

    @Test
    void ac3_4_acallerWithoutAuthorityIsNotToldThatItExists() throws Exception {
        doThrow(new DelegationNotFoundException(12L)).when(delegationService).revoke(12L);

        mockMvc.perform(delete(DELEGATIONS + "/12").with(approver()).with(csrf()))
            .andExpect(status().isNotFound());
    }

    @Test
    void ac3_4_whatHasAlreadyEndedAnswers409() throws Exception {
        doThrowNotActive();

        mockMvc.perform(delete(DELEGATIONS + "/12").with(approver()).with(csrf()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("urn:awards:problem:delegation-not-active"));
    }

    @Test
    void anAnonymousCallerIsNotLetIn() throws Exception {
        mockMvc.perform(get(DELEGATIONS)).andExpect(status().isUnauthorized());

        verify(delegationService, never()).list(any(), anyLong());
    }

    private void doThrowNotActive() {
        doThrow(new ApiProblemException(HttpStatus.CONFLICT, "delegation-not-active",
            "The delegation has already ended")).when(delegationService).revoke(12L);
    }

    private static DelegationResponse delegation(DelegationState state) {
        OrganizationRef faculty = new OrganizationRef(9L, "Faculty of Mathematics and Informatics",
            "Факультет математики та інформатики", "FMI", OrganizationType.FACULTY);
        return new DelegationResponse(12L, RoleType.DEAN, faculty,
            new UserBrief(7L, "Martyn", "Martyniuk", "dean.fmi@chnu.edu.ua"),
            new UserBrief(5L, "Alina", "Kovalenko", "secretary.fmi@chnu.edu.ua"),
            LocalDate.of(2026, 9, 24), LocalDate.of(2026, 10, 8), "Відпустка", state,
            Instant.parse("2026-09-24T08:00:00Z"), null);
    }
}
