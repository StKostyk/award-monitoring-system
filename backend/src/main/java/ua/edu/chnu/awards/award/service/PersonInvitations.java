package ua.edu.chnu.awards.award.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.config.PeopleProperties;
import ua.edu.chnu.awards.award.event.PersonInvited;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;

import lombok.RequiredArgsConstructor;

/**
 * The invitation to register that a person without an account receives: after being added with an address, after
 * a first or new address, and on request at most once per {@code app.people.invitation-interval}.
 */
@Component
@RequiredArgsConstructor
public class PersonInvitations {

    static final String PERSON_CLAIMED = "person-claimed";

    private final PeopleLookup people;
    private final UserAccounts users;
    private final AccessScope access;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final PeopleProperties properties;
    private final Clock clock;

    /**
     * Sends the invitation again.
     *
     * @param id the person
     * @throws ApiProblemException 409 {@code person-claimed} or {@code no-address}, 429 within the interval
     */
    @Transactional
    public void resend(long id) {
        User person = people.lockedInScope(id);
        if (person.getAccountStatus() != AccountStatus.UNCLAIMED) {
            throw claimed();
        }
        if (!person.hasMailbox()) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "no-address",
                "The person's address is unknown, so no invitation can be sent");
        }
        Instant now = clock.instant();
        Instant earliest = person.getInvitedAt() == null ? now
            : person.getInvitedAt().plus(properties.invitationInterval());
        if (now.isBefore(earliest)) {
            long seconds = Math.max(1, Duration.between(now, earliest).toSeconds());
            throw ApiProblemException.tooManyRequests("The invitation was sent a short while ago", seconds);
        }
        send(person);
        users.saveAndFlush(person);
    }

    /**
     * Records and announces an invitation to the person's address; the mail goes once the transaction commits.
     *
     * @param person a person with an address, saved by the caller afterwards
     */
    void send(User person) {
        person.setInvitedAt(clock.instant());
        User sender = users.findById(access.callerId()).orElseThrow();
        Organization unit = person.getOrganization();
        events.publishEvent(new PersonInvited(person.getId(), person.getEmailAddress(), person.getFirstName(),
            sender.getFullName(), unit.getName() == null ? unit.getNameUk() : unit.getName(),
            unit.getNameUk() == null ? unit.getName() : unit.getNameUk(), properties.dataProtectionContact()));
        audit.record(AuditAction.INVITATION_SENT, AuditEntityConstants.USER, access.callerId(), person.getId(),
            Map.of("organizationId", unit.getId()));
    }

    static ApiProblemException claimed() {
        return new ApiProblemException(HttpStatus.CONFLICT, PERSON_CLAIMED,
            "The person has registered, so only they can change their account");
    }
}
