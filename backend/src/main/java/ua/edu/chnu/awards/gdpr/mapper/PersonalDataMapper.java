package ua.edu.chnu.awards.gdpr.mapper;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.auth.entity.UserDevice;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardCategory;
import ua.edu.chnu.awards.award.entity.AwardSnapshot;
import ua.edu.chnu.awards.award.entity.AwardVersion;
import ua.edu.chnu.awards.delegation.entity.RoleDelegation;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.AwardEntry;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.AwardVersionEntry;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.DelegationEntry;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.DeviceEntry;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.NamedRef;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.RoleEntry;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.VersionSnapshot;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;

/**
 * Copies named columns of the entities into export entries, so a new column never reaches the file unnoticed.
 */
@Component
public class PersonalDataMapper {

    /**
     * The account facts, without the password hash.
     *
     * @param user the person
     * @return profile entry
     */
    public PersonalDataFile.Profile profile(User user) {
        Organization department = user.getOrganization();
        return new PersonalDataFile.Profile(user.getEmailAddress(), user.getFirstName(), user.getLastName(),
            ref(department), ref(department.enclosingFaculty()), user.getAccountStatus(), user.getCreatedAt(),
            user.getLastLoginAt());
    }

    /**
     * One role assignment.
     *
     * @param role  the assignment
     * @param today the export day
     * @return role entry
     */
    public RoleEntry role(UserRole role, LocalDate today) {
        return new RoleEntry(role.getRoleType(), ref(role.getOrganization()), role.getValidFrom(), role.getValidTo(),
            role.isCurrentOn(today));
    }

    /**
     * One delegation, naming the other party only.
     *
     * @param delegation the delegation
     * @param direction  {@code GIVEN} or {@code RECEIVED}
     * @param otherParty the other person
     * @param reason     the reason to show, null to leave it out
     * @param today      the export day
     * @return delegation entry
     */
    public DelegationEntry delegation(RoleDelegation delegation, String direction, User otherParty, String reason,
                                      LocalDate today) {
        return new DelegationEntry(direction, otherParty.getFullName(), delegation.getRoleType(),
            ref(delegation.getOrganization()), delegation.getValidFrom(), delegation.getValidTo(),
            delegation.stateOn(today), reason, delegation.getCreatedAt(), delegation.getRevokedAt());
    }

    /**
     * One own award.
     *
     * @param award the award
     * @return award entry
     */
    public AwardEntry award(Award award) {
        AwardCategory category = award.getCategory();
        return new AwardEntry(award.getId(), award.getTitle(), award.getTitleUk(), award.getDescription(),
            award.getDescriptionUk(),
            category == null ? null : new NamedRef(category.getId(), category.getName(), category.getNameUk()),
            award.getAwardingOrganization(), award.getAwardDate(), award.getStatus(), award.getExternalUrl(),
            ref(award.getOrganization()), award.getCreatedAt(), award.getUpdatedAt());
    }

    /**
     * One saved state of an own award, without the actor.
     *
     * @param version the version
     * @return version entry
     */
    public AwardVersionEntry version(AwardVersion version) {
        AwardSnapshot snapshot = version.getSnapshot();
        List<String> changed = version.getChangedFields() == null ? List.of()
            : Arrays.asList(version.getChangedFields());
        return new AwardVersionEntry(version.getAwardId(), version.getNumber(), version.getAction(), changed,
            version.getCreatedAt(), new VersionSnapshot(snapshot.title(), snapshot.titleUk(), snapshot.description(),
            snapshot.descriptionUk(), snapshot.awardingOrganization(), snapshot.awardDate(), snapshot.categoryId(),
            snapshot.status(), snapshot.impactScore(), snapshot.externalUrl(), snapshot.organizationId()));
    }

    /**
     * One browser, without its fingerprint.
     *
     * @param device the device
     * @return device entry
     */
    public DeviceEntry device(UserDevice device) {
        return new DeviceEntry(device.getBrowser(), device.getOperatingSystem(), device.getLastIpAddress(),
            device.getFirstSeenAt(), device.getLastUsedAt());
    }

    private static NamedRef ref(Organization organization) {
        return organization == null ? null
            : new NamedRef(organization.getId(), organization.getName(), organization.getNameUk());
    }
}
