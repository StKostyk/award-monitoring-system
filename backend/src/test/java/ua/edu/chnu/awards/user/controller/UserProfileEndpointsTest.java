package ua.edu.chnu.awards.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import ua.edu.chnu.awards.gdpr.dto.DataExport;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile;
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
    void ac31_theExportIsAnUncachedJsonAttachmentWithSnakeCaseSections() throws Exception {
        PersonalDataFile file = new PersonalDataFile(new PersonalDataFile.Metadata(
            Instant.parse("2026-09-30T09:00:00Z"), 5L, "1.1", "Article 20 - Right to Data Portability"),
            new PersonalDataFile.PersonalData(new PersonalDataFile.Profile("employee.fmi@chnu.edu.ua", "Анастасія",
                "Петренко", null, null, AccountStatus.ACTIVE, null, null)),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(dataExportService.export(5L))
            .thenReturn(new DataExport("award-monitoring-export-2026-09-30.json", file));

        mockMvc.perform(get("/api/v1/users/me/export").with(jwt().jwt(jwt -> jwt.subject("5"))))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(header().string("Content-Disposition",
                "attachment; filename=\"award-monitoring-export-2026-09-30.json\""))
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(jsonPath("$.export_metadata.format_version").value("1.1"))
            .andExpect(jsonPath("$.award_versions").isArray())
            .andExpect(jsonPath("$.export_metadata.export_date").value("2026-09-30T09:00:00Z"))
            .andExpect(jsonPath("$.personal_data.profile.first_name").value("Анастасія"))
            .andExpect(jsonPath("$.personal_data.profile.faculty").isEmpty())
            .andExpect(jsonPath("$.consent_history").isArray())
            .andExpect(jsonPath("$.activity_log").isArray());
    }

    @Test
    void ac34_aRepeatedExportIsTooManyRequests() throws Exception {
        when(dataExportService.export(5L)).thenThrow(new ApiProblemException(HttpStatus.TOO_MANY_REQUESTS,
            "too-many-requests", "Your data was exported a moment ago; try again in a minute"));

        mockMvc.perform(get("/api/v1/users/me/export").with(jwt().jwt(jwt -> jwt.subject("5"))))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.type").value("urn:awards:problem:too-many-requests"));
    }

    @Test
    void ac31_theExportNeedsAToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/export")).andExpect(status().isUnauthorized());
        verifyNoInteractions(dataExportService);
    }

    @Test
    void ac14_aRequestWithoutPasswordIsRefusedBeforeTheService() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/email-change").with(jwt().jwt(jwt -> jwt.subject("12")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"newEmail\":\"mover.new@chnu.edu.ua\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(emailChangeService);
    }
}
