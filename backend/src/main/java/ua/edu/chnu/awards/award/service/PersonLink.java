package ua.edu.chnu.awards.award.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.award.dto.PersonLinkRequest;
import ua.edu.chnu.awards.award.dto.PersonLinkResult;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;
import ua.edu.chnu.awards.user.dto.RoleAssignmentRequest;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.RoleAssignmentService;
import ua.edu.chnu.awards.user.service.RoleHolders;
import ua.edu.chnu.awards.user.service.UserAccounts;

import lombok.RequiredArgsConstructor;

/**
 * Joins a person without an account to the account the person registered on their own: the awards move to the
 * account, the membership is confirmed when the account has no role yet, and the person row is scrubbed.
 */
@Service
@RequiredArgsConstructor
public class PersonLink {

    static final String USER_ID = "userId";

    private final PeopleLookup lookup;
    private final RecipientUnits units;
    private final UserAccounts users;
    private final RoleHolders roleHolders;
    private final RoleAssignmentService roleAssignments;
    private final AwardRepository awards;
    private final PersonRemoval removal;
    private final AwardErasure erasure;
    private final Clock clock;

    /**
     * Joins the person to the account.
     *
     * @param id      the person
     * @param request the account and the person's version
     * @return the account and how many awards moved
     * @throws PersonNotFoundException 404 when the person is unknown, linked or removed, or outside the scope
     * @throws ApiProblemException     422 for a claimed person or an account that cannot take the awards, 409
     *                                 {@code version-conflict} for a stale version
     */
    @Transactional
    public PersonLinkResult link(long id, PersonLinkRequest request) {
        if (request.userId() == null) {
            throw invalid(USER_ID, "required", "Choose the account");
        }
        User person = lookup.lockedInScope(id);
        if (person.getAccountStatus() != AccountStatus.UNCLAIMED) {
            throw invalid("person", "claimed", "The person has registered and claims the awards themselves");
        }
        People.requireVersion(person, request.version());
        long userId = request.userId();
        User account = users.findByIdForUpdate(userId)
            .filter(found -> found.getAccountStatus() == AccountStatus.ACTIVE)
            .filter(found -> found.getId() != id)
            .filter(found -> units.subtree().contains(found.getOrganization().getId()))
            .orElseThrow(PersonLink::unavailable);
        if (roleHolders.findCurrentByUserId(userId, LocalDate.now(clock)).isEmpty()) {
            confirmMembership(userId, person, account);
        }
        erasure.releaseClaimsOnAwardsOf(userId, id);
        int moved = awards.moveOwner(id, userId);
        removal.erase(person, AuditAction.ACCOUNT_LINKED, Map.of("accountId", userId, "movedAwards", moved));
        return new PersonLinkResult(userId, moved);
    }

    private void confirmMembership(long userId, User person, User account) {
        roleAssignments.assign(userId, new RoleAssignmentRequest(RoleType.EMPLOYEE,
            person.getOrganization().getId(), null, null, true, account.getOrganization().getId()));
    }

    private static ApiProblemException unavailable() {
        return invalid(USER_ID, "not-active", "Choose an active account of your faculty");
    }

    private static ApiProblemException invalid(String field, String code, String message) {
        return ApiProblemException.validationFailed(message, List.of(new FieldViolation(field, code, message)));
    }
}
