package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.ReviewDecisionType;
import ua.edu.chnu.awards.award.event.ReviewMeasured;

class ReviewMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ReviewMetrics metrics = new ReviewMetrics(registry);

    @Test
    void ac2_6_aDecisionIsCountedByLevelOutcomeAndPunctualityWithItsDuration() {
        metrics.onMeasured(new ReviewMeasured(ApprovalLevel.DEAN, ReviewDecisionType.APPROVED, true,
            Duration.ofHours(5)));
        metrics.onMeasured(new ReviewMeasured(ApprovalLevel.DEAN, ReviewDecisionType.APPROVED, false,
            Duration.ofHours(7)));

        assertThat(registry.get(ReviewMetrics.DECISIONS).tags("level", "DEAN", "decision", "approved", "on_time",
            "true").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(ReviewMetrics.DECISIONS).tags("on_time", "false").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(ReviewMetrics.DURATION).tag("level", "DEAN").timer().totalTime(TimeUnit.HOURS))
            .isEqualTo(12.0);
    }

    @Test
    void ac2_6_theGaugesFollowTheLatestCountAndMissingLevelsReadZero() {
        metrics.refresh(Map.of(ApprovalLevel.FACULTY_SECRETARY, 4L, ApprovalLevel.DEAN, 2L),
            Map.of(ApprovalLevel.FACULTY_SECRETARY, 3L));
        metrics.refresh(Map.of(ApprovalLevel.FACULTY_SECRETARY, 5L), Map.of(ApprovalLevel.FACULTY_SECRETARY, 1L));

        assertThat(registry.get(ReviewMetrics.OPEN).tag("level", "FACULTY_SECRETARY").gauge().value()).isEqualTo(5.0);
        assertThat(registry.get(ReviewMetrics.OPEN).tag("level", "DEAN").gauge().value()).isZero();
        assertThat(registry.get(ReviewMetrics.OVERDUE).tag("level", "FACULTY_SECRETARY").gauge().value())
            .isEqualTo(1.0);
        assertThat(registry.get(ReviewMetrics.OVERDUE).tag("level", "RECTOR").gauge().value()).isZero();
    }

    @Test
    void ac2_6_sentNoticesAreCounted() {
        metrics.noticeSent();
        metrics.noticeSent();

        assertThat(registry.get(ReviewMetrics.NOTICES).counter().count()).isEqualTo(2.0);
    }
}
