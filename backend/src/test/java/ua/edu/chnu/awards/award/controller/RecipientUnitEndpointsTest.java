package ua.edu.chnu.awards.award.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ua.edu.chnu.awards.award.dto.UnitRef;
import ua.edu.chnu.awards.award.service.RecipientUnits;
import ua.edu.chnu.awards.user.entity.OrganizationType;

@WebMvcTest(RecipientUnitController.class)
class RecipientUnitEndpointsTest extends AbstractAwardEndpointsTest {

    private static final String UNITS = "/api/v1/awards/recipient-units";

    @MockitoBean
    private RecipientUnits recipientUnits;

    @Test
    void ac0_1_recipientUnitsAnswerTheUnitsOfTheCaller() throws Exception {
        when(recipientUnits.list()).thenReturn(List.of(new UnitRef(9L, "Faculty of Mathematics",
            "Факультет математики", OrganizationType.FACULTY)));

        mockMvc.perform(get(UNITS).with(employee()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(9))
            .andExpect(jsonPath("$[0].nameUk").value("Факультет математики"))
            .andExpect(jsonPath("$[0].type").value("FACULTY"));
    }

    @Test
    void ac0_1_withoutATokenTheUnitsAnswer401() throws Exception {
        mockMvc.perform(get(UNITS)).andExpect(status().isUnauthorized());
    }
}
