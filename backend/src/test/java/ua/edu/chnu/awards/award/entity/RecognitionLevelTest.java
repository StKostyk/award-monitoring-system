package ua.edu.chnu.awards.award.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RecognitionLevelTest {

    @ParameterizedTest
    @CsvSource({
        "SPECIALITY, FACULTY_SECRETARY, 10",
        "DEPARTMENT, FACULTY_SECRETARY, 20",
        "COLLEGE, FACULTY_SECRETARY, 30",
        "FACULTY, FACULTY_SECRETARY, 40",
        "UNIVERSITY, FACULTY_SECRETARY, 50",
        "LOCAL, FACULTY_SECRETARY, 60",
        "REGIONAL, FACULTY_SECRETARY, 70",
        "NATIONAL, RECTOR_SECRETARY, 80",
        "INTERNATIONAL, RECTOR_SECRETARY, 100"
    })
    void ac01_eachLevelCarriesItsMinimumApprovalAndBaseScore(RecognitionLevel level, ApprovalLevel approval,
                                                            int score) {
        assertThat(level.minimumApproval()).isEqualTo(approval);
        assertThat(level.baseScore()).isEqualTo(score);
    }

    @Test
    void ac01_theNineLevelsOfTheSchemaAreKnown() {
        assertThat(Arrays.stream(RecognitionLevel.values()).map(Enum::name))
            .containsExactlyInAnyOrder("SPECIALITY", "DEPARTMENT", "FACULTY", "COLLEGE", "UNIVERSITY", "LOCAL",
                "REGIONAL", "NATIONAL", "INTERNATIONAL");
    }

    @Test
    void ac1_onlyNationalAndInternationalClimbPastTheFacultySecretary() {
        assertThat(Arrays.stream(RecognitionLevel.values())
            .filter(level -> level.minimumApproval() != ApprovalLevel.FACULTY_SECRETARY))
            .containsExactly(RecognitionLevel.NATIONAL, RecognitionLevel.INTERNATIONAL);
    }

    @Test
    void ac1_baseScoresRankTheLevelsByReach() {
        assertThat(Arrays.stream(RecognitionLevel.values()).mapToInt(RecognitionLevel::baseScore))
            .isSorted();
    }

    @Test
    void ac01_noLevelNeedsTheRectorAsItsMinimum() {
        assertThat(RecognitionLevel.values())
            .noneMatch(level -> level.minimumApproval() == ApprovalLevel.RECTOR);
    }

    @Test
    void ac01_approvalLevelsFollowTheWorkflowOrder() {
        assertThat(ApprovalLevel.values()).containsExactly(ApprovalLevel.FACULTY_SECRETARY, ApprovalLevel.DEAN,
            ApprovalLevel.RECTOR_SECRETARY, ApprovalLevel.RECTOR);
    }
}
