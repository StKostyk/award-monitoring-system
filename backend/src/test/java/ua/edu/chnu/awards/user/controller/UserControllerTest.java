package ua.edu.chnu.awards.user.controller;

import static org.mockito.ArgumentMatchers.anyLong;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ua.edu.chnu.awards.auth.security.AccessTokenDecoder;
import ua.edu.chnu.awards.auth.security.JwtAuthorityConverter;
import ua.edu.chnu.awards.auth.security.ProblemDetailsEntryPoint;
import ua.edu.chnu.awards.common.web.ApiExceptionHandler;
import ua.edu.chnu.awards.config.LoginSessionConfig;
import ua.edu.chnu.awards.config.SecurityConfig;
import ua.edu.chnu.awards.user.dto.OrganizationRef;
import ua.edu.chnu.awards.user.dto.RoleAssignmentResponse;
import ua.edu.chnu.awards.user.dto.UserProfileResponse;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.service.UserNotFoundException;
import ua.edu.chnu.awards.user.service.UserProfileService;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, LoginSessionConfig.class, JwtAuthorityConverter.class, ProblemDetailsEntryPoint.class,
    ApiExceptionHandler.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserProfileService profileService;

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

    @Test
    void ac13_meReturnsTheProfileOfTheTokenSubject() throws Exception {
        OrganizationRef department = new OrganizationRef(64L, "Department of Algebra and Informatics",
            "Кафедра алгебри та інформатики", "DAI", OrganizationType.DEPARTMENT);
        when(profileService.profileOf(5L)).thenReturn(new UserProfileResponse(5L, "employee.fmi@chnu.edu.ua",
            "Анастасія", "Працівник",
            List.of(new RoleAssignmentResponse(RoleType.EMPLOYEE, department, LocalDate.of(2026, 9, 1), null)),
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
    void unknownSubjectIsNotFoundProblemDetails() throws Exception {
        when(profileService.profileOf(anyLong())).thenThrow(new UserNotFoundException(99L));

        mockMvc.perform(get("/api/v1/users/me").with(jwt().jwt(jwt -> jwt.subject("99"))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("User 99 not found"));
    }
}
