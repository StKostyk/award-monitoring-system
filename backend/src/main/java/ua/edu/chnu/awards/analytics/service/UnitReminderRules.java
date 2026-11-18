package ua.edu.chnu.awards.analytics.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.award.service.UnitStatistics;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.organization.OrganizationTree;
import ua.edu.chnu.awards.organization.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;

import lombok.RequiredArgsConstructor;

/**
 * Who may remind which department, of what, and whom.
 */
@Component
@RequiredArgsConstructor
public class UnitReminderRules {

    private static final Set<RoleType> SENDERS = EnumSet.of(RoleType.FACULTY_SECRETARY, RoleType.DEAN);
    private static final String HAS_AWARDS = "has-awards";

    private final AccessScope access;
    private final OrganizationTree tree;
    private final UserAccounts users;
    private final UnitStatistics statistics;
    private final Clock clock;

    /**
     * Checks the request, the unit and the caller's right to remind it.
     *
     * @param unitId      the unit
     * @param recipientId the colleague to tell, null when missing
     * @throws ApiProblemException   400 {@code invalid-parameter} without a recipient, 409 {@code has-awards} for a
     *                               faculty
     * @throws UnitNotFoundException when the unit is unknown or neither a faculty nor a department
     * @throws AccessDeniedException when no own or delegated faculty secretary or dean role covers the unit
     */
    public void requireSender(long unitId, Long recipientId) {
        if (recipientId == null) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-parameter", "The recipient is required",
                Map.of("parameter", "recipientId"));
        }
        OrganizationType type = tree.node(unitId).map(OrganizationTree.Node::type)
            .filter(found -> found == OrganizationType.FACULTY || found == OrganizationType.DEPARTMENT)
            .orElseThrow(() -> new UnitNotFoundException(unitId));
        boolean allowed = access.scopes().stream()
            .anyMatch(scope -> SENDERS.contains(scope.role()) && tree.covers(scope.organizationId(), unitId));
        if (!allowed) {
            throw new AccessDeniedException("No faculty secretary or dean role covers unit " + unitId);
        }
        if (type == OrganizationType.FACULTY) {
            throw new ApiProblemException(HttpStatus.CONFLICT, HAS_AWARDS, "Only a department can be reminded");
        }
    }

    /**
     * Checks that the department has no counted award in the current Kyiv year.
     *
     * @param unitId the department
     * @return the year
     * @throws ApiProblemException 409 {@code has-awards}
     */
    public int requireNoAwards(long unitId) {
        int year = LocalDate.now(clock).getYear();
        if (!statistics.counts(tree.subtree(unitId), new YearRange(year, year)).isEmpty()) {
            throw new ApiProblemException(HttpStatus.CONFLICT, HAS_AWARDS,
                "The department has an award in " + year);
        }
        return year;
    }

    /**
     * The recipient, an active member of the department.
     *
     * @param unitId      the department
     * @param recipientId the colleague
     * @return the colleague
     * @throws ApiProblemException 422 {@code invalid-recipient}
     */
    public User recipient(long unitId, long recipientId) {
        return users.findById(recipientId)
            .filter(user -> user.getAccountStatus() == AccountStatus.ACTIVE)
            .filter(user -> user.getOrganization() != null && user.getOrganization().getId() == unitId)
            .orElseThrow(() -> new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-recipient",
                "The recipient must be an active member of the department"));
    }
}
