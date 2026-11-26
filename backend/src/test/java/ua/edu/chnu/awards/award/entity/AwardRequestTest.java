package ua.edu.chnu.awards.award.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AwardRequestTest {

    private static final Instant NOTICED = Instant.parse("2026-10-07T08:05:00Z");

    @Test
    void ac2_3_aNewPeriodClearsTheNoticeTogetherWithTheDeadline() {
        AwardRequest request = AwardRequest.builder().currentLevel(ApprovalLevel.FACULTY_SECRETARY)
            .deadline(NOTICED.minusSeconds(3600)).build();
        ReflectionTestUtils.setField(request, "overdueNoticedAt", NOTICED);
        ReflectionTestUtils.setField(request, "overdueNoticedLevel", ApprovalLevel.FACULTY_SECRETARY);
        Instant next = Instant.parse("2026-10-10T21:00:00Z");

        request.restartPeriod(next);

        assertThat(request.getDeadline()).isEqualTo(next);
        assertThat(request.getOverdueNoticedAt()).isNull();
        assertThat(request.getOverdueNoticedLevel()).isNull();

        request.restartPeriod(null);

        assertThat(request.getDeadline()).isNull();
    }
}
