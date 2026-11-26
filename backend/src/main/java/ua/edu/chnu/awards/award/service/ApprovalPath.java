package ua.edu.chnu.awards.award.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;

/**
 * The approval levels a request passes in turn: from the faculty secretary up to the category's minimum
 * approval level, or further when the request already stands higher (an escalation, or a category changed to a
 * lower level after a return).
 */
@Component
public class ApprovalPath {

    /**
     * The levels of a request, lowest first.
     *
     * @param category the recognition level of the award's category, null when the award has none
     * @param current  the level the request stands at
     * @return the levels, never empty
     */
    public List<ApprovalLevel> levels(RecognitionLevel category, ApprovalLevel current) {
        ApprovalLevel minimum = category == null ? ApprovalLevel.FACULTY_SECRETARY : category.minimumApproval();
        ApprovalLevel last = minimum.compareTo(current) >= 0 ? minimum : current;
        return Arrays.stream(ApprovalLevel.values())
            .filter(level -> level.compareTo(last) <= 0)
            .toList();
    }
}
