package ua.edu.chnu.awards.award.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ApprovalLevelTest {

    @ParameterizedTest
    @EnumSource(ApprovalLevel.class)
    void everyLevelIsReviewedByTheRoleOfTheSameName(ApprovalLevel level) {
        assertThat(level.role().name()).isEqualTo(level.name());
    }
}
