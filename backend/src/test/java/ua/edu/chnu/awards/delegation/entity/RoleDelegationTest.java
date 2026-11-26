package ua.edu.chnu.awards.delegation.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class RoleDelegationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);

    @Test
    void ac33_stateFollowsTheCalendarWithoutAnyJob() {
        assertThat(between(TODAY.minusDays(1), TODAY.plusDays(13)).stateOn(TODAY))
            .isEqualTo(DelegationState.ACTIVE);
        assertThat(between(TODAY, TODAY).stateOn(TODAY)).isEqualTo(DelegationState.ACTIVE);
        assertThat(between(TODAY.plusDays(1), TODAY.plusDays(7)).stateOn(TODAY))
            .isEqualTo(DelegationState.UPCOMING);
        assertThat(between(TODAY.minusDays(10), TODAY.minusDays(1)).stateOn(TODAY))
            .isEqualTo(DelegationState.EXPIRED);
    }

    @Test
    void ac34_revocationOutranksTheCalendar() {
        RoleDelegation delegation = between(TODAY, TODAY.plusDays(5));
        delegation.setRevokedAt(Instant.parse("2026-09-24T08:00:00Z"));

        assertThat(delegation.stateOn(TODAY)).isEqualTo(DelegationState.REVOKED);
        assertThat(delegation.isCurrentOn(TODAY)).isFalse();
    }

    @Test
    void ac32_onlyAnActiveDelegationLendsAuthority() {
        assertThat(between(TODAY, TODAY.plusDays(5)).isCurrentOn(TODAY)).isTrue();
        assertThat(between(TODAY.plusDays(1), TODAY.plusDays(5)).isCurrentOn(TODAY)).isFalse();
        assertThat(between(TODAY.minusDays(5), TODAY.minusDays(1)).isCurrentOn(TODAY)).isFalse();
    }

    private static RoleDelegation between(LocalDate from, LocalDate to) {
        return RoleDelegation.builder().validFrom(from).validTo(to).build();
    }
}
