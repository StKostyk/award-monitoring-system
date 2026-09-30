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
import ua.edu.chnu.awards.auth.repository.UserDeviceRepository;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.delegation.repository.RoleDelegationRepository;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.DelegationEntry;
import ua.edu.chnu.awards.gdpr.mapper.PersonalDataMapper;
import ua.edu.chnu.awards.gdpr.repository.PersonalDataQueries;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Builds the export file section by section: no password hash, token, authorization-server record, trigger
 * snapshot or other person's data beyond a delegation party's name.
 */
@Component
@RequiredArgsConstructor
public class PersonalDataAssembler {

    static final String FORMAT_VERSION = "1.0";
    static final String GDPR_ARTICLE = "Article 20 - Right to Data Portability";
    static final String GIVEN = "GIVEN";
    static final String RECEIVED = "RECEIVED";
    static final List<String> ACTIVITY_AREAS = List.of(AuditEntityConstants.AUTHENTICATION,
        AuditEntityConstants.AUTHORIZATION, AuditEntityConstants.USER, AuditEntityConstants.GDPR);
    static final List<String> SELF_ACTIONS = Stream.of(AuditAction.LOGIN_SUCCESS, AuditAction.LOGOUT,
        AuditAction.EMAIL_VERIFIED, AuditAction.PASSWORD_RESET, AuditAction.SECURITY_REVOKE,
        AuditAction.PROFILE_UPDATED, AuditAction.EMAIL_CHANGE_REQUESTED, AuditAction.EMAIL_CHANGED,
        AuditAction.ACCESS_DENIED, AuditAction.DATA_EXPORT).map(Enum::name).toList();

    private final UserRoleRepository roles;
    private final RoleDelegationRepository delegations;
    private final AwardRepository awards;
    private final UserDeviceRepository devices;
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
            roles.findHistoryByUserId(userId).stream().map(role -> mapper.role(role, today)).toList(),
            delegations(userId, today),
            awards.findByOwnerIdOrderByCreatedAtDescIdDesc(userId).stream().map(mapper::award).toList(),
            queries.documents(userId),
            queries.consents(userId),
            devices.findByUserIdOrderByLastUsedAtDesc(userId).stream().map(mapper::device).toList(),
            queries.activity(userId, ACTIVITY_AREAS, SELF_ACTIONS));
    }

    private List<DelegationEntry> delegations(long userId, LocalDate today) {
        List<DelegationEntry> entries = new ArrayList<>();
        delegations.findByDelegatorId(userId).forEach(delegation -> entries.add(
            mapper.delegation(delegation, GIVEN, delegation.getDelegate(), delegation.getReason(), today)));
        delegations.findByDelegateId(userId).forEach(delegation -> entries.add(
            mapper.delegation(delegation, RECEIVED, delegation.getDelegator(), null, today)));
        return entries;
    }
}
