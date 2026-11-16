package ua.edu.chnu.awards.award.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditRedaction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.common.erasure.ErasureContext;
import ua.edu.chnu.awards.common.erasure.ErasureContributor;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * Removal of a person without an account whose awards are all drafts: the same erasure chain as an account
 * erasure runs at once, so the drafts go, the row is scrubbed and its address becomes free, and the trail is
 * redacted.
 */
@Component
@RequiredArgsConstructor
public class PersonRemoval {

    private final PeopleLookup lookup;
    private final AwardRepository awards;
    private final List<ErasureContributor> contributors;
    private final AccessScope access;
    private final AuditService audit;
    private final AuditRedaction redaction;
    private final Clock clock;

    /**
     * Removes the person.
     *
     * @param id the person
     * @throws ApiProblemException 409 {@code person-claimed} or {@code person-has-awards}
     */
    @Transactional
    public void remove(long id) {
        User person = lookup.lockedInScope(id);
        if (person.getAccountStatus() != AccountStatus.UNCLAIMED) {
            throw PersonInvitations.claimed();
        }
        if (awards.existsByOwnerIdAndStatusNot(id, AwardStatus.DRAFT)) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "person-has-awards",
                "The person has awards beyond drafts, so they stay on record");
        }
        final long organizationId = person.getOrganization().getId();
        Set<String> names = new LinkedHashSet<>();
        names.add(person.getFullName());
        names.addAll(redaction.formerNames(id));
        Instant now = clock.instant();
        ErasureContext context = new ErasureContext(id, person.getEmailAddress(), names, now,
            LocalDate.now(clock));
        contributors.stream()
            .sorted(Comparator.comparingInt(ErasureContributor::order))
            .forEach(contributor -> contributor.erase(context));
        audit.record(AuditAction.PERSON_REMOVED, AuditEntityConstants.USER, access.callerId(), id,
            Map.of("organizationId", organizationId));
        redaction.redactSubject(id, context.names(), context.erasedRecords());
    }
}
