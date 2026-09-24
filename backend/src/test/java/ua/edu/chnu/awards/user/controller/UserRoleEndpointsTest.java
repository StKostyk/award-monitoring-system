package ua.edu.chnu.awards.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import ua.edu.chnu.awards.user.dto.OrganizationRef;
import ua.edu.chnu.awards.user.dto.RoleAssignmentResponse;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;

@WebMvcTest(UserController.class)
class UserRoleEndpointsTest extends AbstractUserEndpointsTest {

    @Test
    void ac2_1_grantingARoleAnswers201WithTheAssignment() throws Exception {
        OrganizationRef faculty = new OrganizationRef(9L, "Faculty of Mathematics and Informatics",
            "Факультет математики та інформатики", "FMI", OrganizationType.FACULTY);
        when(roleAssignmentService.assign(eq(5L), any()))
            .thenReturn(new RoleAssignmentResponse(12L, RoleType.FACULTY_SECRETARY, faculty,
                LocalDate.of(2026, 9, 22), LocalDate.of(2026, 10, 22)));

        mockMvc.perform(post("/api/v1/users/5/roles").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"role":"FACULTY_SECRETARY","organizationId":9,"validTo":"2026-10-22"}
                    """)
                .with(jwt().jwt(jwt -> jwt.subject("7").claim("role_scopes", List.of("DEAN:9")))
                    .authorities(new SimpleGrantedAuthority("user:manage:scope")))
                .with(csrf()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(12))
            .andExpect(jsonPath("$.role").value("FACULTY_SECRETARY"))
            .andExpect(jsonPath("$.organization.id").value(9))
            .andExpect(jsonPath("$.validTo").value("2026-10-22"));

        verify(roleAssignmentService).assign(eq(5L), argThat(request ->
            request.role() == RoleType.FACULTY_SECRETARY && !request.updateOrganization()
                && request.organizationId() == 9L && request.validTo().equals(LocalDate.of(2026, 10, 22))));
    }

    @Test
    void ac2_1_abodyWithoutARoleIsRejectedBeforeTheService() throws Exception {
        mockMvc.perform(post("/api/v1/users/5/roles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"organizationId\":9}")
                .with(jwt().jwt(jwt -> jwt.subject("7"))
                    .authorities(new SimpleGrantedAuthority("user:manage:scope")))
                .with(csrf()))
            .andExpect(status().isBadRequest());

        verify(roleAssignmentService, never()).assign(anyLong(), any());
    }

    @Test
    void ac2_2_aCallerWithoutTheManagePermissionGetsATypedForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/users/5/roles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"EMPLOYEE\",\"organizationId\":64}")
                .with(jwt().jwt(jwt -> jwt.subject("5"))
                    .authorities(new SimpleGrantedAuthority("user:read:scope")))
                .with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(content().contentType("application/problem+json"))
            .andExpect(jsonPath("$.type").value("urn:awards:problem:access-denied"))
            .andExpect(jsonPath("$.detail").value("permission user:manage or user:manage:scope is required"));

        verify(roleAssignmentService, never()).assign(anyLong(), any());
    }

    @Test
    void ac2_4_revokingAnswers204() throws Exception {
        mockMvc.perform(delete("/api/v1/users/5/roles/12")
                .with(jwt().jwt(jwt -> jwt.subject("7"))
                    .authorities(new SimpleGrantedAuthority("user:manage")))
                .with(csrf()))
            .andExpect(status().isNoContent());

        verify(roleAssignmentService).revoke(5L, 12L);
    }

    @Test
    void ac2_4_revokingWithoutTheManagePermissionIsForbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/users/5/roles/12")
                .with(jwt().jwt(jwt -> jwt.subject("5"))
                    .authorities(new SimpleGrantedAuthority("user:read:all")))
                .with(csrf()))
            .andExpect(status().isForbidden());

        verify(roleAssignmentService, never()).revoke(anyLong(), anyLong());
    }
}
