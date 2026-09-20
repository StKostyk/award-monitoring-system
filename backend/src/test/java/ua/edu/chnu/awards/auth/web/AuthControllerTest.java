package ua.edu.chnu.awards.auth.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ua.edu.chnu.awards.auth.dto.RegisterRequest;
import ua.edu.chnu.awards.auth.dto.RegistrationResponse;
import ua.edu.chnu.awards.auth.security.AccessTokenDecoder;
import ua.edu.chnu.awards.auth.security.AccountStatusChecker;
import ua.edu.chnu.awards.auth.security.JpaUserDetailsService;
import ua.edu.chnu.awards.auth.security.JwtAuthorityConverter;
import ua.edu.chnu.awards.auth.security.LoginFailureHandler;
import ua.edu.chnu.awards.auth.security.ProblemDetailsEntryPoint;
import ua.edu.chnu.awards.auth.service.RegistrationService;
import ua.edu.chnu.awards.common.web.ApiExceptionHandler;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.config.SecurityConfig;
import ua.edu.chnu.awards.user.entity.AccountStatus;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthorityConverter.class, ProblemDetailsEntryPoint.class, ApiExceptionHandler.class})
class AuthControllerTest {

    private static final String VALID = """
        {"email":"new.user@chnu.edu.ua","password":"correct-horse-battery","firstName":"Олена",
         "lastName":"Нова","organizationId":64}
        """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean(answers = Answers.RETURNS_MOCKS)
    private AccessTokenDecoder accessTokenDecoder;

    @MockitoBean
    private JpaUserDetailsService userDetailsService;

    @MockitoBean
    private AccountStatusChecker statusChecker;

    @MockitoBean
    private LoginFailureHandler failureHandler;

    @Test
    void ac21_registerIsOpenAndAnswersCreated() throws Exception {
        when(registrationService.register(any(RegisterRequest.class)))
            .thenReturn(new RegistrationResponse("new.user@chnu.edu.ua", AccountStatus.PENDING));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void ac22_businessRefusalsAreProblemDetailsWithATypedReason() throws Exception {
        when(registrationService.register(any(RegisterRequest.class))).thenThrow(new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY, "institutional-email-required", "Institutional addresses only"));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentType("application/problem+json"))
            .andExpect(jsonPath("$.type").value("urn:awards:problem:institutional-email-required"))
            .andExpect(jsonPath("$.detail").value("Institutional addresses only"));
    }

    @Test
    void malformedBodyIsBadRequestBeforeTheServiceIsCalled() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"\",\"firstName\":\"<b>\",\"lastName\":\"x\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void ac25_verifyReturnsTheNewStatus() throws Exception {
        when(registrationService.verify("raw"))
            .thenReturn(new RegistrationResponse("new.user@chnu.edu.ua", AccountStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"raw\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void ac26_resendIsAcceptedOrThrottled() throws Exception {
        mockMvc.perform(post("/api/v1/auth/resend-verification").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new.user@chnu.edu.ua\"}"))
            .andExpect(status().isAccepted());

        doThrow(new ApiProblemException(HttpStatus.TOO_MANY_REQUESTS, "too-many-requests", "Wait a minute"))
            .when(registrationService).resend("new.user@chnu.edu.ua");
        mockMvc.perform(post("/api/v1/auth/resend-verification").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new.user@chnu.edu.ua\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.type").value("urn:awards:problem:too-many-requests"));
    }
}
