package ua.edu.chnu.awards.common.retention;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

class RetentionPolicyTest {

    @Test
    void ac3_2_periodsOfWholeDaysAreWrittenInDaysAndShorterOnesAsTheyAre() {
        assertThat(RetentionPolicy.days(Duration.ofDays(30))).isEqualTo("P30D");
        assertThat(RetentionPolicy.days(Duration.ofMinutes(5))).isEqualTo("PT5M");
    }

    @Test
    void ac3_5_theHeldPeopleBecomeASortedArrayLiteralAndCutOffsCountCalendarYears() {
        RetentionContext context = new RetentionContext(Instant.parse("2026-10-19T00:30:00Z"), Set.of(17L, 4L), 1,
            TransactionOperations.withoutTransaction());

        assertThat(context.heldArray()).isEqualTo("{4,17}");
        assertThat(new RetentionContext(Instant.EPOCH, Set.of(), 1, TransactionOperations.withoutTransaction())
            .heldArray()).isEqualTo("{}");
        assertThat(context.before(Period.ofYears(7))).isEqualTo(Instant.parse("2019-10-19T00:30:00Z"));
    }
}
