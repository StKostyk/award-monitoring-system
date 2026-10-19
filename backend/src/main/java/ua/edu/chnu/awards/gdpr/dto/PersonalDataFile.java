package ua.edu.chnu.awards.gdpr.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.AwardVisibility;
import ua.edu.chnu.awards.award.entity.ReviewDecisionType;
import ua.edu.chnu.awards.award.entity.VersionAction;
import ua.edu.chnu.awards.delegation.entity.DelegationState;
import ua.edu.chnu.awards.user.entity.AccountStatus;

/**
 * Everything the system holds about one person, in the machine-readable form of GDPR Article 20
 * (PRIVACY_BY_DESIGN section 6.2). Sections without entries are empty lists, never absent.
 *
 * @param exportMetadata  when, for whom and in which format version the file was made
 * @param personalData    the profile
 * @param roles           role assignments past, present and future
 * @param delegations     delegations given and received
 * @param awards          own awards in every status
 * @param awardVersions   every saved state of those awards, oldest first per award
 * @param documents       metadata of the documents of those awards
 * @param reviewDecisions reviewer decisions on those awards, oldest first
 * @param decisionsMade   decisions made as a reviewer or under a role the person lent, oldest first
 * @param consentHistory  every consent record
 * @param legalHolds      legal holds placed on the account, newest first
 * @param erasureRequests requests to erase the account, newest first
 * @param devices         browsers the account signed in from
 * @param activityLog     the person's own application events, newest first
 * @param notifications   in-app notifications and the delivery log of mails, newest first
 * @param notificationPreferences the notification choices the person changed from the default
 * @param dashboardPreferences    the widgets hidden on the personal dashboard; none and no time when never saved
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PersonalDataFile(Metadata exportMetadata, PersonalData personalData, List<RoleEntry> roles,
                               List<DelegationEntry> delegations, List<AwardEntry> awards,
                               List<AwardVersionEntry> awardVersions, List<DocumentEntry> documents,
                               List<ReviewDecisionEntry> reviewDecisions, List<DecisionMadeEntry> decisionsMade,
                               List<ConsentEntry> consentHistory, List<LegalHoldEntry> legalHolds,
                               List<ErasureRequestEntry> erasureRequests, List<DeviceEntry> devices,
                               List<ActivityEntry> activityLog, List<NotificationEntry> notifications,
                               List<NotificationPreferenceEntry> notificationPreferences,
                               DashboardPreferenceEntry dashboardPreferences) {

    /**
     * Keeps unmodifiable copies of the sections.
     */
    public PersonalDataFile {
        roles = List.copyOf(roles);
        delegations = List.copyOf(delegations);
        awards = List.copyOf(awards);
        awardVersions = List.copyOf(awardVersions);
        documents = List.copyOf(documents);
        reviewDecisions = List.copyOf(reviewDecisions);
        decisionsMade = List.copyOf(decisionsMade);
        consentHistory = List.copyOf(consentHistory);
        legalHolds = List.copyOf(legalHolds);
        erasureRequests = List.copyOf(erasureRequests);
        devices = List.copyOf(devices);
        activityLog = List.copyOf(activityLog);
        notifications = List.copyOf(notifications);
        notificationPreferences = List.copyOf(notificationPreferences);
    }

    /**
     * Number of entries per list section, as the audit trail keeps them.
     *
     * @return section name to entry count, in file order
     */
    public Map<String, Object> sectionCounts() {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("roles", roles.size());
        counts.put("delegations", delegations.size());
        counts.put("awards", awards.size());
        counts.put("award_versions", awardVersions.size());
        counts.put("documents", documents.size());
        counts.put("review_decisions", reviewDecisions.size());
        counts.put("decisions_made", decisionsMade.size());
        counts.put("consent_history", consentHistory.size());
        counts.put("legal_holds", legalHolds.size());
        counts.put("erasure_requests", erasureRequests.size());
        counts.put("devices", devices.size());
        counts.put("activity_log", activityLog.size());
        counts.put("notifications", notifications.size());
        counts.put("notification_preferences", notificationPreferences.size());
        return counts;
    }

    /**
     * Facts about the file itself.
     *
     * @param exportDate    the moment of the export
     * @param userId        the person exported
     * @param formatVersion version of this structure
     * @param gdprArticle   the right the file answers
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Metadata(Instant exportDate, long userId, String formatVersion, String gdprArticle) {
    }

    /**
     * The personal data section.
     *
     * @param profile the account facts
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PersonalData(Profile profile) {
    }

    /**
     * The account facts.
     *
     * @param email         the sign-in address
     * @param firstName     first name
     * @param lastName      last name
     * @param department    the unit the person belongs to
     * @param faculty       the faculty above it, null when there is none
     * @param accountStatus account state
     * @param createdAt     registration moment
     * @param lastLoginAt   last sign-in, null when never
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Profile(String email, String firstName, String lastName, NamedRef department, NamedRef faculty,
                          AccountStatus accountStatus, Instant createdAt, Instant lastLoginAt) {
    }

    /**
     * A bilingual named reference.
     *
     * @param id     identifier
     * @param name   English name
     * @param nameUk Ukrainian name, may be null
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record NamedRef(long id, String name, String nameUk) {
    }

    /**
     * One role assignment.
     *
     * @param role         the role
     * @param organization where it applies
     * @param validFrom    first day
     * @param validTo      last day, null when open-ended
     * @param current      whether it is in effect on the export day
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RoleEntry(RoleType role, NamedRef organization, LocalDate validFrom, LocalDate validTo,
                            boolean current) {
    }

    /**
     * One delegation; the other party appears by name only.
     *
     * @param direction    {@code GIVEN} or {@code RECEIVED}
     * @param otherParty   full name of the other person
     * @param role         the lent role
     * @param organization where it applies
     * @param validFrom    first day
     * @param validTo      last day
     * @param state        where it stands on the export day
     * @param reason       the reason the person gave, null for received delegations
     * @param createdAt    when it was created
     * @param revokedAt    when it was taken back, null when not
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DelegationEntry(String direction, String otherParty, RoleType role, NamedRef organization,
                                  LocalDate validFrom, LocalDate validTo, DelegationState state, String reason,
                                  Instant createdAt, Instant revokedAt) {
    }

    /**
     * One own award: received personally, or entered for a faculty or department.
     *
     * @param awardId              identifier
     * @param title                English title
     * @param titleUk              Ukrainian title
     * @param description          English description
     * @param descriptionUk        Ukrainian description
     * @param category             the category, null when not chosen
     * @param awardingOrganization who granted it
     * @param awardDate            when it was granted
     * @param status               workflow status
     * @param visibility           who sees an approved personal award, null for a unit award
     * @param externalUrl          link to an external record
     * @param organization         the unit it is recorded for
     * @param recipientUnit        the faculty or department that received it, null for a personal award
     * @param createdAt            creation moment
     * @param updatedAt            last change
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AwardEntry(long awardId, String title, String titleUk, String description, String descriptionUk,
                             NamedRef category, String awardingOrganization, LocalDate awardDate, AwardStatus status,
                             AwardVisibility visibility, String externalUrl, NamedRef organization,
                             NamedRef recipientUnit, Instant createdAt, Instant updatedAt) {
    }

    /**
     * One saved state of an own award.
     *
     * @param awardId       the award
     * @param version       version number within the award
     * @param action        what produced the version
     * @param changedFields fields changed against the previous version, empty for the first one
     * @param createdAt     when it was saved
     * @param snapshot      the business fields at that moment
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AwardVersionEntry(long awardId, long version, VersionAction action, List<String> changedFields,
                                    Instant createdAt, VersionSnapshot snapshot) {

        /**
         * Keeps an unmodifiable copy of the changed fields.
         */
        public AwardVersionEntry {
            changedFields = List.copyOf(changedFields);
        }
    }

    /**
     * The business fields of an award in one version.
     *
     * @param title                English title
     * @param titleUk              Ukrainian title
     * @param description          English description
     * @param descriptionUk        Ukrainian description
     * @param awardingOrganization who granted it
     * @param awardDate            when it was granted
     * @param categoryId           the category
     * @param status               workflow status
     * @param impactScore          impact score
     * @param externalUrl          link to an external record
     * @param organizationId       the unit it is recorded for
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record VersionSnapshot(String title, String titleUk, String description, String descriptionUk,
                                  String awardingOrganization, LocalDate awardDate, Long categoryId,
                                  AwardStatus status, Integer impactScore, String externalUrl, Long organizationId) {
    }

    /**
     * Metadata of one document; the file itself is fetched through the API path.
     *
     * @param documentId identifier
     * @param awardId    the award it belongs to
     * @param fileName   original file name
     * @param fileType   file extension
     * @param mimeType   content type
     * @param fileSize   size in bytes
     * @param uploadedAt upload moment
     * @param apiPath    where the file can be downloaded
     * @param shownToColleagues whether the owner shows it to colleagues while the award is shared
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DocumentEntry(long documentId, Long awardId, String fileName, String fileType, String mimeType,
                                long fileSize, Instant uploadedAt, String apiPath, boolean shownToColleagues) {
    }

    /**
     * One reviewer decision on an own award; the reviewer and the role holder appear by name only.
     *
     * @param awardId      the award
     * @param awardTitle   English title of the award, the Ukrainian one when it has none
     * @param awardTitleUk Ukrainian title of the award, the English one when it has none
     * @param decision     what was decided
     * @param level        the approval level it was made at
     * @param decidedAt    when it was made
     * @param comments     the reviewer comment, null when none
     * @param reviewer     full name of the reviewer
     * @param delegator    full name of the holder of the role the reviewer borrowed, null under an own role
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReviewDecisionEntry(long awardId, String awardTitle, String awardTitleUk,
                                      ReviewDecisionType decision, ApprovalLevel level, Instant decidedAt,
                                      String comments, String reviewer, String delegator) {
    }

    /**
     * One decision the person made as a reviewer, or a delegate made under a role the person lent; of the award only
     * its titles are given.
     *
     * @param awardId      the award
     * @param awardTitle   English title of the award, the Ukrainian one when it has none
     * @param awardTitleUk Ukrainian title of the award, the English one when it has none
     * @param decision     what was decided
     * @param level        the approval level it was made at
     * @param decidedAt    when it was made
     * @param comments     the reviewer comment, null when none
     * @param reviewer     full name of the reviewer
     * @param delegator    full name of the holder of the borrowed role, null under an own role
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DecisionMadeEntry(long awardId, String awardTitle, String awardTitleUk, ReviewDecisionType decision,
                                    ApprovalLevel level, Instant decidedAt, String comments, String reviewer,
                                    String delegator) {
    }

    /**
     * One consent record.
     *
     * @param consentType    what was consented to
     * @param consentVersion policy version
     * @param granted        state of the record
     * @param grantedAt      when granted
     * @param withdrawnAt    when withdrawn
     * @param ipAddress      the person's address at the time
     * @param createdAt      record moment
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ConsentEntry(String consentType, String consentVersion, boolean granted, Instant grantedAt,
                               Instant withdrawnAt, String ipAddress, Instant createdAt) {
    }

    /**
     * One legal hold on the account; the reasons and the officers stay out of the file.
     *
     * @param placedAt when the hold was placed
     * @param liftedAt when it was lifted, null while active
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record LegalHoldEntry(Instant placedAt, Instant liftedAt) {
    }

    /**
     * One request to erase the account.
     *
     * @param status        PENDING, CANCELLED or COMPLETED
     * @param requestedAt   when the person asked
     * @param scheduledFor  when the account is erased
     * @param warningSentAt when the reminder was mailed, or null
     * @param postponedAt   when a legal hold first postponed the erasure, or null
     * @param cancelledAt   when the person cancelled, or null
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ErasureRequestEntry(String status, Instant requestedAt, Instant scheduledFor,
                                      Instant warningSentAt, Instant postponedAt, Instant cancelledAt) {
    }

    /**
     * One browser the account signed in from.
     *
     * @param browser         browser family
     * @param operatingSystem operating-system family
     * @param lastIpAddress   address of the latest sign-in
     * @param firstSeenAt     first sign-in
     * @param lastUsedAt      latest sign-in
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DeviceEntry(String browser, String operatingSystem, String lastIpAddress, Instant firstSeenAt,
                              Instant lastUsedAt) {
    }

    /**
     * One application event of the person.
     *
     * @param action    what happened
     * @param timestamp when
     * @param ipAddress the person's address, null when another person acted on the account
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ActivityEntry(String action, Instant timestamp, String ipAddress) {
    }

    /**
     * One notification: an in-app row or the delivery log of a mail.
     *
     * @param type           the notification type
     * @param channel        {@code IN_APP} or {@code EMAIL}
     * @param payload        the values of its sentence
     * @param read           whether an in-app notification was read
     * @param sentAt         when it was written, or when the mail was handed to sending
     * @param readAt         when it was read, null when not
     * @param deliveredAt    when the mail server accepted the mail, null otherwise
     * @param deliveryStatus {@code SENDING}, {@code SENT} or {@code FAILED} for mails, null for in-app rows
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record NotificationEntry(String type, String channel, Map<String, Object> payload, boolean read,
                                    Instant sentAt, Instant readAt, Instant deliveredAt, String deliveryStatus) {

        /**
         * Keeps an unmodifiable copy of the payload; its values may be null.
         */
        public NotificationEntry {
            payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        }
    }

    /**
     * One notification type the person changed from the default (both channels on).
     *
     * @param type  the notification type
     * @param email whether mails of the type are sent
     * @param inApp whether in-app notifications of the type are written
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record NotificationPreferenceEntry(String type, boolean email, boolean inApp) {
    }

    /**
     * The widget choice of the personal dashboard.
     *
     * @param hiddenWidgets the hidden widget names, empty when every widget is shown
     * @param updatedAt     when the choice was saved, null when never
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DashboardPreferenceEntry(List<String> hiddenWidgets, Instant updatedAt) {

        /**
         * Keeps an unmodifiable copy of the names.
         */
        public DashboardPreferenceEntry {
            hiddenWidgets = List.copyOf(hiddenWidgets);
        }
    }
}
