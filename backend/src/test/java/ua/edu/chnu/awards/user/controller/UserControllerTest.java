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
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditLog;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.security.AccessTokenDecoder;
import ua.edu.chnu.awards.auth.security.JwtAuthorityConverter;
import ua.edu.chnu.awards.auth.security.LoginAccessDeniedHandler;
import ua.edu.chnu.awards.auth.security.ProblemDetailsEntryPoint;
import ua.edu.chnu.awards.auth.security.RolePermissions;
import ua.edu.chnu.awards.authz.AccessDenials;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.authz.OrganizationTree;
import ua.edu.chnu.awards.authz.ProblemDetailsAccessDeniedHandler;
import ua.edu.chnu.awards.authz.RoleLevels;
import ua.edu.chnu.awards.common.web.ApiExceptionHandler;
import ua.edu.chnu.awards.config.InfrastructureConfig;
import ua.edu.chnu.awards.config.LoginSessionConfig;
import ua.edu.chnu.awards.config.SecurityConfig;
import ua.edu.chnu.awards.user.dto.OrganizationRef;
import ua.edu.chnu.awards.user.dto.RoleAssignmentResponse;
import ua.edu.chnu.awards.user.dto.UserDirectoryQuery;
import ua.edu.chnu.awards.user.dto.UserProfileResponse;
import ua.edu.chnu.awards.user.dto.UserSummaryResponse;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.service.UserDirectoryService;
import ua.edu.chnu.awards.user.service.UserNotFoundException;
import ua.edu.chnu.awards.user.service.UserProfileService;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, LoginSessionConfig.class, InfrastructureConfig.class, JwtAuthorityConverter.class,
    ProblemDetailsEntryPoint.class, LoginAccessDeniedHandler.class, ApiExceptionHandler.class, AccessScope.class,
    RoleLevels.class, RolePermissions.class, AccessDenials.class, ProblemDetailsAccessDeniedHandler.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserProfileService profileService;

    @MockitoBean
    private UserDirectoryService directoryService;

    @MockitoBean
    private OrganizationTree tree;

    @MockitoBean
    private AuditService audit;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean(answers = Answers.RETURNS_MOCKS)
    private AccessTokenDecoder accessTokenDecoder;

    @MockitoBean
    private ua.edu.chnu.awards.auth.security.JpaUserDetailsService userDetailsService;

    @MockitoBean
    private ua.edu.chnu.awards.auth.security.AccountStatusChecker statusChecker;

    @MockitoBean
    private ua.edu.chnu.awards.auth.security.LoginFailureHandler failureHandler;

    @MockitoBean
    private ua.edu.chnu.awards.auth.security.LockedAccountChecker lockChecker;

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private StringRedisTemplate redisTemplate;

    @Test
    void ac13_meReturnsTheProfileOfTheTokenSubject() throws Exception {
        OrganizationRef department = new OrganizationRef(64L, "Department of Algebra and Informatics",
            "Кафедра алгебри та інформатики", "DAI", OrganizationType.DEPARTMENT);
        when(profileService.profileOf(5L)).thenReturn(new UserProfileResponse(5L, "employee.fmi@chnu.edu.ua",
            "Анастасія", "Працівник",
            List.of(new RoleAssignmentResponse(1L, RoleType.EMPLOYEE, department, LocalDate.of(2026, 9, 1), null)),
            department, AccountStatus.ACTIVE, Instant.parse("2026-09-01T00:00:00Z"), null));

        mockMvc.perform(get("/api/v1/users/me")
                .with(jwt().jwt(jwt -> jwt.subject("5").claim("email", "employee.fmi@chnu.edu.ua"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.email").value("employee.fmi@chnu.edu.ua"))
            .andExpect(jsonPath("$.roles[0].role").value("EMPLOYEE"))
            .andExpect(jsonPath("$.roles[0].organization.code").value("DAI"))
            .andExpect(jsonPath("$.organization.type").value("DEPARTMENT"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
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

        verify(audit).record(eq(AuditAction.ACCESS_DENIED), eq(AuditLog.AUTHORIZATION), eq(5L),
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

        verify(audit).record(eq(AuditAction.ACCESS_DENIED), eq(AuditLog.AUTHORIZATION), eq(5L),
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
