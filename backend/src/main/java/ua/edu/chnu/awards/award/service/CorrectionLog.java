package ua.edu.chnu.awards.award.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.award.dto.FieldChange;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardCategory;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.event.AwardCorrected;
import ua.edu.chnu.awards.user.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * The traces a correction leaves besides the award version: its audit row and the owner's e-mail after commit.
 */
@Component
@RequiredArgsConstructor
public class CorrectionLog {

    private static final String CATEGORY = "categoryId";
    private static final String IMPACT_SCORE = "impactScore";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final AuditService audit;
    private final ReviewerRule rule;
    private final ApplicationEventPublisher events;

    /**
     * Writes the {@code AWARD_CORRECTED} audit row with the old and new values, the reason and the delegator.
     *
     * @param request    the request of the corrected award
     * @param reviewerId who corrected it
     * @param changes    the changed snapshot fields
     * @param reason     why the reviewer corrected it
     */
    public void audit(AwardRequest request, long reviewerId, List<FieldChange> changes, String reason) {
        Map<String, Object> details = new HashMap<>();
        details.put("requestId", request.getId());
        details.put("level", request.getCurrentLevel().name());
        details.put("changes", changes.stream()
            .map(change -> new FieldChange(change.field(), text(change.from()), text(change.to())))
            .toList());
        details.put("reason", reason);
        Optional.ofNullable(rule.delegatorId(request)).ifPresent(id -> details.put("delegatorId", id));
        audit.record(AuditAction.AWARD_CORRECTED, AuditEntityConstants.AWARDS, reviewerId,
            request.getAward().getId(), details);
    }

    /**
     * Publishes the correction for the owner's e-mail, sent once the transaction commits. The derived impact
     * score is left out, categories are named in both languages and dates are shown day first.
     *
     * @param award    the corrected award
     * @param reviewer who corrected it
     * @param reason   why
     * @param changes  the changed snapshot fields
     * @param before   the category before the correction, null when none
     */
    public void announce(Award award, User reviewer, String reason, List<FieldChange> changes,
                         AwardCategory before) {
        User owner = award.getOwner();
        String title = award.getTitle() == null ? award.getTitleUk() : award.getTitle();
        String titleUk = award.getTitleUk() == null ? award.getTitle() : award.getTitleUk();
        List<AwardCorrected.Change> mailed = changes.stream()
            .filter(change -> !IMPACT_SCORE.equals(change.field()))
            .map(change -> CATEGORY.equals(change.field())
                ? new AwardCorrected.Change(CATEGORY, name(before, false), name(award.getCategory(), false),
                    name(before, true), name(award.getCategory(), true))
                : new AwardCorrected.Change(change.field(), shown(change.from()), shown(change.to()),
                    shown(change.from()), shown(change.to())))
            .toList();
        events.publishEvent(new AwardCorrected(owner.getEmailAddress(), owner.getFirstName(), award.getId(), title,
            titleUk, reviewer.getFullName(), reason, mailed));
    }

    private static String name(AwardCategory category, boolean ukrainian) {
        if (category == null) {
            return null;
        }
        return ukrainian ? category.getNameUk() : category.getName();
    }

    private static String shown(Object value) {
        return value instanceof LocalDate date ? DATE.format(date) : text(value);
    }

    private static String text(Object value) {
        return Objects.toString(value, null);
    }
}
