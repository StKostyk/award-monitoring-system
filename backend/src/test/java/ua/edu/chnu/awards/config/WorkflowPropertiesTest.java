package ua.edu.chnu.awards.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorkflowPropertiesTest {

    private static final int FIVE_DAYS = 5;

    @Test
    void ac2_aPositiveNumberOfWorkingDaysIsKept() {
        assertThat(new WorkflowProperties(FIVE_DAYS).reviewWorkingDays()).isEqualTo(FIVE_DAYS);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void ac2_aReviewPeriodThatIsNotPositiveStopsTheStart(int days) {
        assertThatThrownBy(() -> new WorkflowProperties(days)).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("app.workflow.review-working-days");
    }
}
