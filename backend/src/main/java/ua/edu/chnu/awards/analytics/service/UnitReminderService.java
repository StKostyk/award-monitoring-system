package ua.edu.chnu.awards.analytics.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.analytics.config.ReminderProperties;
import ua.edu.chnu.awards.analytics.dto.UnitReminder;
import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.dto.UserRef;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.notification.event.UnitReminderRequested;
import ua.edu.chnu.awards.organization.dto.OrganizationRef;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.service.OrganizationLookup;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;

import lombok.RequiredArgsConstructor;

/**
 * The «Нагадати завідувачу» reminder: a faculty secretary or dean, own or delegated role, reminds a colleague of a
 * department in the role's scope that has no counted award this Kyiv year, at most once per department within
 * {@code app.reminders.unit-interval}.
 */
@Service
@RequiredArgsConstructor
public class UnitReminderService {

    private final UnitReminderRules rules;
    private final UnitReminderLog log;
    private final AccessScope access;
    private final OrganizationLookup organizations;
    private final UserAccounts users;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ReminderProperties properties;

    /**
     * Sends the reminder under the department's lock; the recipient is told in the app and by mail once the
     * transaction commits.
     *
     * @param unitId      the department
     * @param recipientId the colleague to tell
     * @return the accepted reminder
     * @throws UnitNotFoundException when the unit is unknown or neither a faculty nor a department
     * @throws AccessDeniedException when no own or delegated faculty secretary or dean role covers the unit
     * @throws ApiProblemException   400 {@code invalid-parameter} without a recipient, 409 {@code has-awards} or
     *                               {@code recently-reminded} with {@code lastSentAt}, 422 {@code invalid-recipient}
     */
    @Transactional
    public UnitReminder remind(long unitId, Long recipientId) {
        rules.requireSender(unitId, recipientId);
        log.lock(unitId);
        Instant now = Instant.now(clock);
        Instant last = log.lastSent(List.of(unitId), now.minus(properties.unitInterval())).get(unitId);
        if (last != null) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "recently-reminded",
                "The department was reminded at " + last, Map.of("lastSentAt", last.toString()));
        }
        int year = rules.requireNoAwards(unitId);
        User recipient = rules.recipient(unitId, recipientId);
        Organization unit = organizations.findById(unitId).orElseThrow(() -> new UnitNotFoundException(unitId));
        long senderId = access.callerId();
        String sender = users.findById(senderId).map(User::getFullName).orElse("");
        audit.record(AuditAction.UNIT_REMINDED, AuditEntityConstants.ORGANIZATIONS, senderId, unitId,
            Map.of("recipientId", recipientId, "year", year));
        String unitUk = unit.getNameUk() == null ? unit.getName() : unit.getNameUk();
        events.publishEvent(new UnitReminderRequested(recipient.getId(), recipient.getEmailAddress(),
            recipient.getFirstName(), unitId, unit.getName(), unitUk, year, sender, now));
        return new UnitReminder(new OrganizationRef(unit.getId(), unit.getName(), unit.getNameUk(), unit.getCode(),
            unit.getOrgType()), UserRef.of(recipient), now);
    }
}
