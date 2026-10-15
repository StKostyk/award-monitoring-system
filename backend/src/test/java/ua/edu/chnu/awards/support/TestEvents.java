package ua.edu.chnu.awards.support;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.event.AwardDecided;
import ua.edu.chnu.awards.award.event.ReviewTakenOver;
import ua.edu.chnu.awards.delegation.event.DelegationCreated;
import ua.edu.chnu.awards.delegation.event.DelegationRevoked;
import ua.edu.chnu.awards.user.event.RoleAssigned;

/**
 * Builds domain events for notification tests: Олена (id 7) as recipient, her award 5 «Грамота», the Faculty of
 * Mathematics and Informatics, no occurrence time; each test sets only what it is about.
 */
public final class TestEvents {

    private static final Long USER_ID = 7L;
    private static final String EMAIL = "olena@chnu.edu.ua";
    private static final String FIRST_NAME = "Олена";
    private static final long AWARD_ID = 5L;
    private static final String TITLE = "Certificate";
    private static final String TITLE_UK = "Грамота";
    private static final String REVIEWER = "Ірина Бойко";
    private static final String ORGANIZATION = "FMI";
    private static final String ORGANIZATION_UK = "ФМІ";
    private static final String ACTOR = "Адміністратор";
    private static final LocalDate VALID_FROM = LocalDate.of(2026, 10, 1);

    private TestEvents() {
    }

    /**
     * A dean's approval of award 5.
     *
     * @return the builder
     */
    public static Decided decided() {
        return new Decided();
    }

    /**
     * A take-over of award 5 by Ірина Бойко.
     *
     * @return the builder
     */
    public static TakenOver takenOver() {
        return new TakenOver();
    }

    /**
     * A dean role open from 2026-10-01 without an end.
     *
     * @return the builder
     */
    public static Assignment roleAssigned() {
        return new Assignment();
    }

    /**
     * A dean delegation from Ірина Бойко open from 2026-10-01 without an end or reason.
     *
     * @return the builder
     */
    public static Delegation delegationCreated() {
        return new Delegation();
    }

    /**
     * A revoked dean delegation of Ірина Бойко.
     *
     * @param recipients who is told
     * @return the builder
     */
    public static Revocation delegationRevoked(DelegationRevoked.Recipient... recipients) {
        return new Revocation(List.of(recipients));
    }

    /**
     * The recipient and the occurrence time of an event addressed to one member.
     *
     * @param <B> the concrete builder
     */
    public abstract static class AbstractPersonal<B extends AbstractPersonal<B>> {

        private Long userId = USER_ID;
        private String email = EMAIL;
        private String firstName = FIRST_NAME;
        private Instant occurredAt;

        /**
         * Sets the member told about the event.
         *
         * @param id        user id, null for a bare address
         * @param address   email address, null when the member has none
         * @param givenName first name in the salutation
         * @return this builder
         */
        public B to(Long id, String address, String givenName) {
            userId = id;
            email = address;
            firstName = givenName;
            return self();
        }

        /**
         * Sets when the event happened.
         *
         * @param instant the time
         * @return this builder
         */
        public B at(Instant instant) {
            occurredAt = instant;
            return self();
        }

        Long userId() {
            return userId;
        }

        String email() {
            return email;
        }

        String firstName() {
            return firstName;
        }

        Instant occurredAt() {
            return occurredAt;
        }

        @SuppressWarnings("unchecked")
        private B self() {
            return (B) this;
        }
    }

    /**
     * Builds {@link AwardDecided}.
     */
    public static final class Decided extends AbstractPersonal<Decided> {

        private long awardId = AWARD_ID;
        private String title = TITLE;
        private String titleUk = TITLE_UK;
        private RequestStatus outcome = RequestStatus.APPROVED;
        private ApprovalLevel level = ApprovalLevel.DEAN;
        private String reviewer = REVIEWER;
        private String comment;

        /**
         * Sets the decided award.
         *
         * @param id        award id
         * @param english   English title
         * @param ukrainian Ukrainian title
         * @return this builder
         */
        public Decided award(long id, String english, String ukrainian) {
            awardId = id;
            title = english;
            titleUk = ukrainian;
            return this;
        }

        /**
         * Sets the outcome and the level that reached it.
         *
         * @param status the outcome
         * @param at     the approval level
         * @return this builder
         */
        public Decided outcome(RequestStatus status, ApprovalLevel at) {
            outcome = status;
            level = at;
            return this;
        }

        /**
         * Sets the reviewer and the comment.
         *
         * @param name reviewer's full name
         * @param text comment, null for none
         * @return this builder
         */
        public Decided by(String name, String text) {
            reviewer = name;
            comment = text;
            return this;
        }

        /**
         * Builds the event.
         *
         * @return the event
         */
        public AwardDecided build() {
            return new AwardDecided(userId(), email(), firstName(), awardId, title, titleUk, outcome, level, reviewer,
                comment, occurredAt());
        }
    }

    /**
     * Builds {@link ReviewTakenOver}.
     */
    public static final class TakenOver extends AbstractPersonal<TakenOver> {

        private long awardId = AWARD_ID;
        private String title = TITLE;
        private String titleUk = TITLE_UK;
        private String reviewer = REVIEWER;

        /**
         * Sets the award taken over.
         *
         * @param id        award id
         * @param english   English title
         * @param ukrainian Ukrainian title
         * @return this builder
         */
        public TakenOver award(long id, String english, String ukrainian) {
            awardId = id;
            title = english;
            titleUk = ukrainian;
            return this;
        }

        /**
         * Sets who took the request over.
         *
         * @param name full name
         * @return this builder
         */
        public TakenOver by(String name) {
            reviewer = name;
            return this;
        }

        /**
         * Builds the event.
         *
         * @return the event
         */
        public ReviewTakenOver build() {
            return new ReviewTakenOver(userId(), email(), firstName(), awardId, title, titleUk, reviewer,
                occurredAt());
        }
    }

    /**
     * Builds {@link RoleAssigned}.
     */
    public static final class Assignment extends AbstractPersonal<Assignment> {

        private RoleType role = RoleType.DEAN;
        private String organization = ORGANIZATION;
        private String organizationUk = ORGANIZATION_UK;
        private String actor = ACTOR;
        private LocalDate validFrom = VALID_FROM;
        private LocalDate validTo;

        /**
         * Sets the role and its unit.
         *
         * @param type      the role
         * @param english   English unit name
         * @param ukrainian Ukrainian unit name
         * @return this builder
         */
        public Assignment role(RoleType type, String english, String ukrainian) {
            role = type;
            organization = english;
            organizationUk = ukrainian;
            return this;
        }

        /**
         * Sets who assigned the role.
         *
         * @param name full name
         * @return this builder
         */
        public Assignment by(String name) {
            actor = name;
            return this;
        }

        /**
         * Sets the validity period.
         *
         * @param from first day
         * @param to   last day, null for open-ended
         * @return this builder
         */
        public Assignment valid(LocalDate from, LocalDate to) {
            validFrom = from;
            validTo = to;
            return this;
        }

        /**
         * Builds the event.
         *
         * @return the event
         */
        public RoleAssigned build() {
            return new RoleAssigned(userId(), email(), firstName(), role, organization, organizationUk, actor,
                validFrom, validTo, occurredAt());
        }
    }

    /**
     * Builds {@link DelegationCreated}.
     */
    public static final class Delegation extends AbstractPersonal<Delegation> {

        private RoleType role = RoleType.DEAN;
        private String organization = ORGANIZATION;
        private String organizationUk = ORGANIZATION_UK;
        private String delegator = REVIEWER;
        private LocalDate validFrom = VALID_FROM;
        private LocalDate validTo;
        private String reason;

        /**
         * Sets the delegated role and its unit.
         *
         * @param type      the role
         * @param english   English unit name
         * @param ukrainian Ukrainian unit name
         * @return this builder
         */
        public Delegation role(RoleType type, String english, String ukrainian) {
            role = type;
            organization = english;
            organizationUk = ukrainian;
            return this;
        }

        /**
         * Sets the delegator and the reason.
         *
         * @param name full name of the delegator
         * @param why  reason, null for none
         * @return this builder
         */
        public Delegation by(String name, String why) {
            delegator = name;
            reason = why;
            return this;
        }

        /**
         * Sets the validity period.
         *
         * @param from first day
         * @param to   last day, null for open-ended
         * @return this builder
         */
        public Delegation valid(LocalDate from, LocalDate to) {
            validFrom = from;
            validTo = to;
            return this;
        }

        /**
         * Builds the event.
         *
         * @return the event
         */
        public DelegationCreated build() {
            return new DelegationCreated(userId(), email(), firstName(), role, organization, organizationUk,
                delegator, validFrom, validTo, reason, occurredAt());
        }
    }

    /**
     * Builds {@link DelegationRevoked}.
     */
    public static final class Revocation {

        private final List<DelegationRevoked.Recipient> recipients;
        private RoleType role = RoleType.DEAN;
        private String organization = ORGANIZATION;
        private String organizationUk = ORGANIZATION_UK;
        private String delegate = REVIEWER;
        private String actor = ACTOR;
        private LocalDate validTo;
        private Instant occurredAt;

        private Revocation(List<DelegationRevoked.Recipient> recipients) {
            this.recipients = recipients;
        }

        /**
         * Sets the delegated role and its unit.
         *
         * @param type      the role
         * @param english   English unit name
         * @param ukrainian Ukrainian unit name
         * @return this builder
         */
        public Revocation role(RoleType type, String english, String ukrainian) {
            role = type;
            organization = english;
            organizationUk = ukrainian;
            return this;
        }

        /**
         * Sets the delegate and who revoked the delegation.
         *
         * @param delegateName full name of the delegate
         * @param actorName    full name of who revoked it
         * @return this builder
         */
        public Revocation of(String delegateName, String actorName) {
            delegate = delegateName;
            actor = actorName;
            return this;
        }

        /**
         * Sets the last day the delegation was valid.
         *
         * @param lastDay the day, null for unknown
         * @return this builder
         */
        public Revocation until(LocalDate lastDay) {
            validTo = lastDay;
            return this;
        }

        /**
         * Sets when the delegation was revoked.
         *
         * @param instant the time
         * @return this builder
         */
        public Revocation at(Instant instant) {
            occurredAt = instant;
            return this;
        }

        /**
         * Builds the event.
         *
         * @return the event
         */
        public DelegationRevoked build() {
            return new DelegationRevoked(recipients, role, organization, organizationUk, delegate, actor, validTo,
                occurredAt);
        }
    }
}
