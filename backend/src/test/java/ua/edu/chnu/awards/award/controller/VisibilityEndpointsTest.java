package ua.edu.chnu.awards.award.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ua.edu.chnu.awards.award.dto.AwardVisibilityUpdate;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.AwardVisibility;
import ua.edu.chnu.awards.award.service.AwardNotFoundException;
import ua.edu.chnu.awards.award.service.AwardSharing;
import ua.edu.chnu.awards.common.web.ApiProblemException;

@WebMvcTest(SharingController.class)
class VisibilityEndpointsTest extends AbstractAwardEndpointsTest {

    private static final String VISIBILITY = "/api/v1/awards/5/visibility";
    private static final String PROBLEM = "urn:awards:problem:";

    @MockitoBean
    private AwardSharing sharing;

    @Test
    void ac1_2_theOwnerChoosesTheVisibility() throws Exception {
        when(sharing.update(5L, new AwardVisibilityUpdate(AwardVisibility.UNIVERSITY)))
            .thenReturn(award(AwardStatus.APPROVED));

        mockMvc.perform(put(VISIBILITY).with(employee()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"UNIVERSITY\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.visibility").value("PRIVATE"));
    }

    @Test
    void ac1_4_anUnknownValueIsAFieldError() throws Exception {
        mockMvc.perform(put(VISIBILITY).with(employee()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"FRIENDS\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.type").value(PROBLEM + "validation-failed"))
            .andExpect(jsonPath("$.errors[0].field").value("visibility"));
        verifyNoInteractions(sharing);
    }

    @Test
    void ac1_4_anAwardThatIsNotApprovedAnswers409() throws Exception {
        when(sharing.update(eq(5L), any())).thenThrow(new ApiProblemException(HttpStatus.CONFLICT,
            "visibility-fixed", "Only an approved award can be shown to others", Map.of("awardStatus", "PENDING")));

        mockMvc.perform(put(VISIBILITY).with(employee()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"PUBLIC\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value(PROBLEM + "visibility-fixed"))
            .andExpect(jsonPath("$.awardStatus").value("PENDING"));
    }

    @Test
    void ac1_4_someoneElsesAwardAnswers404() throws Exception {
        when(sharing.update(eq(5L), any())).thenThrow(new AwardNotFoundException(5L));

        mockMvc.perform(put(VISIBILITY).with(employee()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"PUBLIC\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void ac1_4_aCallerWithoutTheUpdatePermissionGets403() throws Exception {
        mockMvc.perform(put(VISIBILITY).with(administrator()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"PUBLIC\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(sharing);
    }
}
