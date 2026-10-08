package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.award.dto.AwardCorrectionRequest;
import ua.edu.chnu.awards.award.dto.AwardForm;
import ua.edu.chnu.awards.award.dto.FieldChange;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardCategory;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.support.TestAwards;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.User;

class CorrectionRulesTest {

    private final CorrectionRules rules = new CorrectionRules();

    @Test
    void ac3_7_theReasonIsRequiredAndAtMost1000Characters() {
        assertThat(rules.reason("  Дата з наказу ")).isEqualTo("Дата з наказу");
        assertThat(rules.reason("я".repeat(CorrectionRules.REASON_MAX))).hasSize(CorrectionRules.REASON_MAX);
        assertThatThrownBy(() -> rules.reason(null)).isInstanceOf(ApiProblemException.class)
            .hasMessageContaining("required");
        assertThatThrownBy(() -> rules.reason("   ")).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(() -> rules.reason("я".repeat(CorrectionRules.REASON_MAX + 1)))
            .isInstanceOf(ApiProblemException.class).hasMessageContaining("1000");
    }

    @Test
    void ac3_7_aCorrectionWithoutChangesIsRefused() {
        assertThatThrownBy(() -> rules.requireChange(List.of())).isInstanceOf(ApiProblemException.class)
            .extracting("type").isEqualTo("no-change");
        rules.requireChange(List.of(new FieldChange("titleUk", "А", "Б")));
    }

    @Test
    void ac3_3_theSentFieldsAreLaidOverTheCurrentFormAndNullClearsOne() {
        User owner = TestUsers.person(21L, "owner@chnu.edu.ua",
            TestUsers.organization(TestUsers.DAI_DEPARTMENT_ID, OrganizationType.DEPARTMENT));
        Award award = TestAwards.award(owner, owner.getOrganization()).titleUk("Грамота")
            .category(AwardCategory.builder().id(13L).build()).awardingOrganization("МОН")
            .awardDate(LocalDate.of(2025, 5, 1)).externalUrl("https://mon.gov.ua").build();
        AwardForm current = rules.form(award);
        AwardCorrectionRequest body = new AwardCorrectionRequest(null, Optional.of("Подяка"), null, null,
            Optional.of(21L), null, null, Optional.empty(), 4L, 2L, "Назва");

        AwardForm corrected = body.over(current);

        assertThat(current.categoryId()).isEqualTo(13L);
        assertThat(current.version()).isEqualTo(TestAwards.VERSION);
        assertThat(corrected).isEqualTo(new AwardForm("Letter", "Подяка", null, null, 21L, "МОН",
            LocalDate.of(2025, 5, 1), null, null, 4L));
        assertThat(rules.form(TestAwards.award(owner, owner.getOrganization()).build()).categoryId()).isNull();
    }
}
