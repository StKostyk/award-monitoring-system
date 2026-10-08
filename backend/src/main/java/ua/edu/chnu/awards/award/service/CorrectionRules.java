package ua.edu.chnu.awards.award.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.dto.AwardForm;
import ua.edu.chnu.awards.award.dto.FieldChange;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;

/**
 * Rules of a correction besides those of the award form: a reason is required and something must change.
 */
@Component
public class CorrectionRules {

    static final int REASON_MAX = 1000;
    private static final String REASON = "reason";

    /**
     * The reason without surrounding spaces.
     *
     * @param sent the reason as sent
     * @return the cleaned reason
     * @throws ApiProblemException 422 {@code validation-failed} on {@code reason} when it is empty or too long
     */
    public String reason(String sent) {
        String reason = sent == null ? "" : sent.strip();
        if (reason.isEmpty()) {
            throw invalidReason("required", "A reason for the correction is required");
        }
        if (reason.length() > REASON_MAX) {
            throw invalidReason("too-long", "At most " + REASON_MAX + " characters");
        }
        return reason;
    }

    /**
     * Refuses a correction that changes nothing.
     *
     * @param changes the changed snapshot fields
     * @throws ApiProblemException 422 {@code no-change}
     */
    public void requireChange(List<FieldChange> changes) {
        if (changes.isEmpty()) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "no-change",
                "The correction changes no field", Map.of());
        }
    }

    /**
     * The award's current fields as the award form, without a recipient.
     *
     * @param award the award
     * @return its form
     */
    public AwardForm form(Award award) {
        return new AwardForm(award.getTitle(), award.getTitleUk(), award.getDescription(), award.getDescriptionUk(),
            award.getCategory() == null ? null : award.getCategory().getId(), award.getAwardingOrganization(),
            award.getAwardDate(), award.getExternalUrl(), null, award.getVersion());
    }

    private static ApiProblemException invalidReason(String code, String message) {
        return ApiProblemException.validationFailed(message, List.of(new FieldViolation(REASON, code, message)));
    }
}
