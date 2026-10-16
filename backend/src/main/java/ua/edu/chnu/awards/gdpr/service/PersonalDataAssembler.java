package ua.edu.chnu.awards.gdpr.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.auth.service.DeviceLookup;
import ua.edu.chnu.awards.award.service.AwardLookup;
import ua.edu.chnu.awards.award.service.AwardVersionLookup;
import ua.edu.chnu.awards.delegation.service.DelegationLookup;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.DelegationEntry;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.NotificationEntry;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.NotificationPreferenceEntry;
import ua.edu.chnu.awards.gdpr.mapper.PersonalDataMapper;
import ua.edu.chnu.awards.gdpr.repository.PersonalDataQueries;
import ua.edu.chnu.awards.notification.service.NotificationLookup;
import ua.edu.chnu.awards.notification.service.NotificationLookup.NotificationRecord;
import ua.edu.chnu.awards.notification.service.NotificationLookup.PreferenceRecord;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.RoleHolders;

import lombok.RequiredArgsConstructor;

/**
 * Builds the export file section by section: no password hash, token, authorization-server record, trigger
 * snapshot or other person's data beyond the names of delegation parties and reviewers.
 */
@Component
@RequiredArgsConstructor
public class PersonalDataAssembler {

    static final String FORMAT_VERSION = "1.6";
    static final String GDPR_ARTICLE = "Article 20 - Right to Data Portability";
    static final String GIVEN = "GIVEN";
    static final String RECEIVED = "RECEIVED";
    static final List<String> ACTIVITY_AREAS = List.of(AuditEntityConstants.AUTHENTICATION,
        AuditEntityConstants.AUTHORIZATION, AuditEntityConstants.USER, AuditEntityConstants.GDPR);
    static final List<String> SELF_ACTIONS = Stream.of(AuditAction.LOGIN_SUCCESS, AuditAction.LOGOUT,
        AuditAction.EMAIL_VERIFIED, AuditAction.PASSWORD_RESET, AuditAction.SECURITY_REVOKE,
        AuditAction.PROFILE_UPDATED, AuditAction.EMAIL_CHANGE_REQUESTED, AuditAction.EMAIL_CHANGED,
        AuditAction.ACCESS_DENIED, AuditAction.DATA_EXPORT).map(Enum::name).toList();

    private final RoleHolders roleHolders;
    private final DelegationLookup delegations;
    private final AwardLookup awards;
    private final AwardVersionLookup versions;
    private final DeviceLookup devices;
    private final NotificationLookup notifications;
    private final PersonalDataQueries queries;
    private final PersonalDataMapper mapper;
    private final Clock clock;

    /**
     * Collects everything held about the person.
     *
     * @param user the person
     * @return the export file
     */
    @Transactional(readOnly = true)
    public PersonalDataFile assemble(User user) {
        long userId = user.getId();
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, clock.getZone());
        return new PersonalDataFile(
            new PersonalDataFile.Metadata(now, userId, FORMAT_VERSION, GDPR_ARTICLE),
            new PersonalDataFile.PersonalData(mapper.profile(user)),
            roleHolders.findHistoryByUserId(userId).stream().map(role -> mapper.role(role, today)).toList(),
            delegations(userId, today),
            awards.findByOwnerIdOrderByCreatedAtDescIdDesc(userId).stream().map(mapper::award).toList(),
            versions.findOfOwner(userId).stream().map(mapper::version).toList(),
            queries.documents(userId),
            queries.reviewDecisions(userId),
            queries.decisionsMade(userId),
            queries.consents(userId),
            queries.legalHolds(userId),
            queries.erasureRequests(userId),
            devices.findByUserIdOrderByLastUsedAtDesc(userId).stream().map(mapper::device).toList(),
            queries.activity(userId, ACTIVITY_AREAS, SELF_ACTIONS),
            notifications.notificationsOf(userId).stream().map(PersonalDataAssembler::notification).toList(),
            notifications.preferencesOf(userId).stream().map(PersonalDataAssembler::preference).toList());
    }

    private List<DelegationEntry> delegations(long userId, LocalDate today) {
        List<DelegationEntry> entries = new ArrayList<>();
        delegations.findByDelegatorId(userId).forEach(delegation -> entries.add(
            mapper.delegation(delegation, GIVEN, delegation.getDelegate(), delegation.getReason(), today)));
        delegations.findByDelegateId(userId).forEach(delegation -> entries.add(
            mapper.delegation(delegation, RECEIVED, delegation.getDelegator(), null, today)));
        return entries;
    }

    private static NotificationEntry notification(NotificationRecord row) {
        return new NotificationEntry(row.type(), row.channel(), row.payload(), row.read(), row.sentAt(),
            row.readAt(), row.deliveredAt(), row.deliveryStatus());
    }

    private static NotificationPreferenceEntry preference(PreferenceRecord row) {
        return new NotificationPreferenceEntry(row.type(), row.email(), row.inApp());
    }
}
