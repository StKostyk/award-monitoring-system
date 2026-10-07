package ua.edu.chnu.awards.award.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.dto.ReviewDecisionRequest;
import ua.edu.chnu.awards.award.dto.ReviewDecisionRequest.Decision;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;
import ua.edu.chnu.awards.document.repository.DocumentRepository;

import lombok.RequiredArgsConstructor;

/**
 * What a decision body must hold: a decision and the request version, a comment to return or reject of at most
 * {@value #MAX_COMMENT} characters, and documents behind a "documents verified" approval.
 */
@Component
@RequiredArgsConstructor
public class DecisionRules {

    /** Longest reviewer comment. */
    public static final int MAX_COMMENT = 2000;

    private static final String COMMENT = "comment";

    private final DocumentRepository documents;

    /**
     * Refuses an incomplete decision.
     *
     * @param body the decision
     * @throws ApiProblemException 422 {@code validation-failed} naming every missing or invalid field
     */
    public void check(ReviewDecisionRequest body) {
        List<FieldViolation> violations = new ArrayList<>();
        if (body.decision() == null) {
            violations.add(new FieldViolation("decision", "required", "The decision is required"));
        }
        if (body.requestVersion() == null) {
            violations.add(new FieldViolation("requestVersion", "required", "The version last read is required"));
        }
        boolean needsComment = body.decision() == Decision.REJECT || body.decision() == Decision.RETURN;
        if (needsComment && comment(body) == null) {
            violations.add(new FieldViolation(COMMENT, "required", "A comment is required to return or reject"));
        } else if (body.comment() != null && body.comment().strip().length() > MAX_COMMENT) {
            violations.add(new FieldViolation(COMMENT, "too-long", "The comment is longer than " + MAX_COMMENT));
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.validationFailed("The decision cannot be applied", violations);
        }
    }

    /**
     * Refuses a "documents verified" approval of an award without documents.
     *
     * @param awardId the award
     * @param body    the decision
     * @throws ApiProblemException 422 {@code validation-failed} on {@code verified}
     */
    public void checkVerified(long awardId, ReviewDecisionRequest body) {
        if (body.isVerified() && documents.countByAwardId(awardId) == 0) {
            throw ApiProblemException.validationFailed("The award has no documents to verify",
                List.of(new FieldViolation("verified", "no-documents", "The award has no documents to verify")));
        }
    }

    /**
     * The comment without surrounding blanks.
     *
     * @param body the decision
     * @return the comment, null when blank or absent
     */
    public static String comment(ReviewDecisionRequest body) {
        return body.comment() == null || body.comment().isBlank() ? null : body.comment().strip();
    }
}
