package ua.edu.chnu.awards.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.user.dto.OrganizationRef;
import ua.edu.chnu.awards.user.dto.UserProfileResponse;
import ua.edu.chnu.awards.user.dto.UserUpdateRequest;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.service.ProfileNameRules;

@WebMvcTest(UserProfileController.class)
class UserProfileEndpointsTest extends AbstractUserEndpointsTest {

    private static final OrganizationRef DEPARTMENT = new OrganizationRef(64L,
        "Department of Algebra and Informatics", "Кафедра алгебри та інформатики", "DAI",
        OrganizationType.DEPARTMENT);

    @Test
    void ac12_patchPassesTheNamesAndReturnsTheUpdatedProfile() throws Exception {
        when(profileService.update(eq(5L), any())).thenReturn(new UserProfileResponse(5L,
            "employee.fmi@chnu.edu.ua", "Анастасія", "Петренко-Коваль", List.of(), DEPARTMENT, null,
            AccountStatus.ACTIVE, Instant.parse("2026-09-01T00:00:00Z"), null, true));

        mockMvc.perform(patch("/api/v1/users/me").with(jwt().jwt(jwt -> jwt.subject("5")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"lastName\":\"Петренко-Коваль\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lastName").value("Петренко-Коваль"));

        ArgumentCaptor<UserUpdateRequest> request = ArgumentCaptor.forClass(UserUpdateRequest.class);
        verify(profileService).update(eq(5L), request.capture());
        assertThat(request.getValue().getLastName()).isEqualTo("Петренко-Коваль");
        assertThat(request.getValue().getFirstName()).isNull();
    }

    @Test
    void ac12_unknownPropertiesReachTheRulesAndAreRefusedWith422() throws Exception {
        when(profileService.update(anyLong(), any())).thenAnswer(invocation ->
            new ProfileNameRules().check(invocation.getArgument(1)));

        mockMvc.perform(patch("/api/v1/users/me").with(jwt().jwt(jwt -> jwt.subject("5")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"x@chnu.edu.ua\",\"organizationId\":1,\"status\":\"ACTIVE\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.type").value("urn:awards:problem:validation-failed"))
            .andExpect(jsonPath("$.errors[0].field").value("email"))
            .andExpect(jsonPath("$.errors[0].code").value("not-allowed"))
            .andExpect(jsonPath("$.errors[1].field").value("organizationId"))
            .andExpect(jsonPath("$.errors[2].field").value("status"));
    }

    @Test
    void ac12_patchWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(profileService);
    }

    @Test
    void ac14_theAddressChangeIsAcceptedForTheTokenSubject() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/email-change").with(jwt().jwt(jwt -> jwt.subject("12")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\":\"mover.new@chnu.edu.ua\",\"currentPassword\":\"Passw0rd-demo\"}"))
            .andExpect(status().isAccepted());

        verify(emailChangeService).request(12L, "mover.new@chnu.edu.ua", "Passw0rd-demo");
    }

    @Test
    void ac14_aWrongPasswordIsAProblemWithItsType() throws Exception {
        doThrow(new ApiProblemException(HttpStatus.FORBIDDEN, "password-mismatch", "The password is wrong"))
            .when(emailChangeService).request(anyLong(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/users/me/email-change").with(jwt().jwt(jwt -> jwt.subject("12")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\":\"mover.new@chnu.edu.ua\",\"currentPassword\":\"wrong\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.type").value("urn:awards:problem:password-mismatch"));
    }

    @Test
    void ac14_aRequestWithoutPasswordIsRefusedBeforeTheService() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/email-change").with(jwt().jwt(jwt -> jwt.subject("12")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"newEmail\":\"mover.new@chnu.edu.ua\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(emailChangeService);
    }
}
