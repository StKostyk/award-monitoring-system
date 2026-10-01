package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;

class ApprovalPathTest {

    private final ApprovalPath path = new ApprovalPath();

    @ParameterizedTest
    @CsvSource({
        "DEPARTMENT, FACULTY_SECRETARY, FACULTY_SECRETARY",
        "FACULTY, FACULTY_SECRETARY, DEAN",
        "COLLEGE, DEAN, DEAN",
        "NATIONAL, FACULTY_SECRETARY, RECTOR_SECRETARY",
        "INTERNATIONAL, DEAN, RECTOR_SECRETARY",
        "DEPARTMENT, DEAN, DEAN",
        "FACULTY, RECTOR, RECTOR"
    })
    void ac1_3_thePathClimbsFromTheSecretaryToTheHigherOfMinimumAndCurrentLevel(RecognitionLevel category,
                                                                              ApprovalLevel current,
                                                                              ApprovalLevel last) {
        assertThat(path.levels(category, current))
            .startsWith(ApprovalLevel.FACULTY_SECRETARY)
            .endsWith(last)
            .hasSize(last.ordinal() + 1);
    }

    @ParameterizedTest
    @CsvSource({"FACULTY_SECRETARY, 1", "DEAN, 2"})
    void edge_anAwardWithoutCategoryStopsAtItsCurrentLevel(ApprovalLevel current, int size) {
        assertThat(path.levels(null, current)).hasSize(size).endsWith(current);
    }
}
