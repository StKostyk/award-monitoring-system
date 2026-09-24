package ua.edu.chnu.awards.user.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.user.dto.OrganizationRef;
import ua.edu.chnu.awards.user.dto.RoleAssignmentResponse;
import ua.edu.chnu.awards.user.dto.UserDirectoryQuery;
import ua.edu.chnu.awards.user.dto.UserProfileResponse;
import ua.edu.chnu.awards.user.dto.UserSummaryResponse;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.service.UserNotFoundException;

@WebMvcTest(UserController.class)
class UserControllerTest extends AbstractUserEndpointsTest {

    @Test
    void ac13_meReturnsTheProfileOfTheTokenSubject() throws Exception {
        OrganizationRef department = new OrganizationRef(64L, "Department of Algebra and Informatics",
            "Кафедра алгебри та інформатики", "DAI", OrganizationType.DEPARTMENT);
        when(profileService.profileOf(5L)).thenReturn(new UserProfileResponse(5L, "employee.fmi@chnu.edu.ua",
            "Анастасія", "Працівник",
            List.of(new RoleAssignmentResponse(1L, RoleType.EMPLOYEE, department, LocalDate.of(2026, 9, 1), null)),
            department, AccountStatus.ACTIVE, Instant.parse("2026-09-01T00:00:00Z"), null, true));

        mockMvc.perform(get("/api/v1/users/me")
                .with(jwt().jwt(jwt -> jwt.subject("5").claim("email", "employee.fmi@chnu.edu.ua"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.email").value("employee.fmi@chnu.edu.ua"))
            .andExpect(jsonPath("$.roles[0].role").value("EMPLOYEE"))
            .andExpect(jsonPath("$.roles[0].organization.code").value("DAI"))
            .andExpect(jsonPath("$.organization.type").value("DEPARTMENT"))
            .andExpect(jsonPath("$.membershipConfirmed").value(true))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void ac2_6_meReportsAnUnconfirmedMembershipWithNoRoles() throws Exception {
        OrganizationRef department = new OrganizationRef(64L, "Department of Algebra and Informatics",
            "Кафедра алгебри та інформатики", "DAI", OrganizationType.DEPARTMENT);
        when(profileService.profileOf(6L)).thenReturn(new UserProfileResponse(6L, "newcomer@chnu.edu.ua",
            "Новий", "Працівник", List.of(), department, AccountStatus.ACTIVE,
            Instant.parse("2026-09-22T00:00:00Z"), null, false));

        mockMvc.perform(get("/api/v1/users/me").with(jwt().jwt(jwt -> jwt.subject("6"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles").isEmpty())
            .andExpect(jsonPath("$.membershipConfirmed").value(false))
            .andExpect(jsonPath("$.organization.id").value(64));
    }

    @Test
    void ac13_meWithoutTokenIsUnauthorizedProblemDetails() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("WWW-Authenticate", "Bearer"))
            .andExpect(content().contentType("application/problem+json"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void ac16_theDirectoryIsPagedForReaders() throws Exception {
        OrganizationRef department = new OrganizationRef(64L, "Department of Algebra and Informatics",
            "Кафедра алгебри та інформатики", "DAI", OrganizationType.DEPARTMENT);
        UserSummaryResponse row = new UserSummaryResponse(5L, "employee.fmi@chnu.edu.ua", "Анастасія",
            "Працівник", department, AccountStatus.ACTIVE, List.of(), false);
        when(directoryService.list(new UserDirectoryQuery(null, RoleType.EMPLOYEE, null, true, "пра"), 1, 100))
            .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(1, 100), 101));

        mockMvc.perform(get("/api/v1/users").param("role", "EMPLOYEE").param("unconfirmed", "true")
                .param("q", "пра").param("page", "1").param("size", "100")
                .with(jwt().jwt(jwt -> jwt.subject("7").claim("role_scopes", List.of("DEAN:9")))
                    .authorities(new SimpleGrantedAuthority("user:read:scope"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].email").value("employee.fmi@chnu.edu.ua"))
            .andExpect(jsonPath("$.content[0].membershipConfirmed").value(false))
            .andExpect(jsonPath("$.totalElements").value(101))
            .andExpect(jsonPath("$.number").value(1))
            .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void ac15_aCallerWithoutTheReadPermissionGetsATypedForbiddenAndAnAuditRow() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                .with(jwt().jwt(jwt -> jwt.subject("5")).authorities(new SimpleGrantedAuthority("award:create"))))
            .andExpect(status().isForbidden())
            .andExpect(content().contentType("application/problem+json"))
            .andExpect(jsonPath("$.type").value("urn:awards:problem:access-denied"))
            .andExpect(jsonPath("$.detail").value("permission user:read:all or user:read:scope is required"));

        verify(audit).recordSeparately(eq(AuditAction.ACCESS_DENIED), eq("AUTHORIZATION"), eq(5L),
            argThat(details -> "/api/v1/users".equals(details.get("path")) && "GET".equals(details.get("method"))
                && "permission user:read:all or user:read:scope is required".equals(details.get("required"))));
    }

    @Test
    void ac15_aRequestRuleRefusalIsTheSameTypedProblemAndIsAudited() throws Exception {
        mockMvc.perform(get("/actuator/env")
                .with(jwt().jwt(jwt -> jwt.subject("5")).authorities(new SimpleGrantedAuthority("award:create"))))
            .andExpect(status().isForbidden())
            .andExpect(content().contentType("application/problem+json"))
            .andExpect(jsonPath("$.type").value("urn:awards:problem:access-denied"))
            .andExpect(jsonPath("$.detail").value("authority ROLE_SYSTEM_ADMIN is required"))
            .andExpect(jsonPath("$.instance").value("/actuator/env"));

        verify(audit).recordSeparately(eq(AuditAction.ACCESS_DENIED), eq("AUTHORIZATION"), eq(5L),
            argThat(details -> "/actuator/env".equals(details.get("path"))
                && "authority ROLE_SYSTEM_ADMIN is required".equals(details.get("required"))));
    }

    @Test
    void ac13_anOrganisationOutsideTheScopeIsForbidden() throws Exception {
        when(tree.covers(9L, 10L)).thenReturn(false);

        mockMvc.perform(get("/api/v1/users").param("organization", "10")
                .with(jwt().jwt(jwt -> jwt.subject("7").claim("role_scopes", List.of("DEAN:9")))
                    .authorities(new SimpleGrantedAuthority("user:read:scope"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("organisation 10 is outside your scope"));
    }

    @Test
    void ac17_aUserOutsideTheScopeIsNotFound() throws Exception {
        when(directoryService.detail(3L)).thenThrow(new UserNotFoundException(3L));

        mockMvc.perform(get("/api/v1/users/3")
                .with(jwt().jwt(jwt -> jwt.subject("7")).authorities(new SimpleGrantedAuthority("user:read:scope"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void unknownSubjectIsNotFoundProblemDetails() throws Exception {
        when(profileService.profileOf(anyLong())).thenThrow(new UserNotFoundException(99L));

        mockMvc.perform(get("/api/v1/users/me").with(jwt().jwt(jwt -> jwt.subject("99"))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("User 99 not found"));
    }
}
