package ua.edu.chnu.awards.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorkflowPropertiesTest {

    @Test
    void ac1_1_aPositiveReviewPeriodIsKept() {
        assertThat(new WorkflowProperties(Duration.ofDays(5)).reviewPeriod()).isEqualTo(Duration.ofDays(5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "-P1D"})
    void edge_aReviewPeriodThatIsNotPositiveStopsTheStart(String period) {
        Duration value = Duration.parse(period);

        assertThatThrownBy(() -> new WorkflowProperties(value)).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("app.workflow.review-period");
        assertThatThrownBy(() -> new WorkflowProperties(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
