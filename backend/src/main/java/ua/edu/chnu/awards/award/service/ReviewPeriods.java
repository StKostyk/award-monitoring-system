package ua.edu.chnu.awards.award.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.authz.OrganizationTree;
import ua.edu.chnu.awards.award.dto.ReviewPeriodResponse;
import ua.edu.chnu.awards.award.dto.ReviewPeriodUpdate;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;

import lombok.RequiredArgsConstructor;

/**
 * The review period of a faculty: read by its faculty secretaries and deans, changed by its dean (own role or a
 * delegation in effect) or a system administrator. Any other organisation, or one outside the caller's scope, is
 * not found. A change only affects deadlines set after it.
 */
@Service
@RequiredArgsConstructor
public class ReviewPeriods {

    /** Fewest working days a faculty may set. */
    public static final int MIN_WORKING_DAYS = 1;
    /** Most working days a faculty may set. */
    public static final int MAX_WORKING_DAYS = 20;

    private static final String FIELD = "workingDays";
    private static final String SYSTEM_CONFIGURE = "system:configure";
    private static final Set<RoleType> READERS = Set.of(RoleType.FACULTY_SECRETARY, RoleType.DEAN);

    private final OrganizationRepository organizations;
    private final OrganizationTree tree;
    private final AccessScope access;
    private final StatusEstimator estimator;
    private final AuditService audit;

    /**
     * The review period of a faculty.
     *
     * @param facultyId the faculty
     * @return its own, effective and default period and whether the caller may change it
     * @throws FacultyNotFoundException when the organisation is no faculty or the caller may not read it
     */
    @Transactional(readOnly = true)
    public ReviewPeriodResponse read(long facultyId) {
        Organization faculty = faculty(facultyId);
        boolean updatable = changeAuthority(facultyId).isPresent();
        if (!updatable && !reads(facultyId)) {
            throw new FacultyNotFoundException(facultyId);
        }
        return response(faculty, updatable);
    }

    /**
     * Sets a faculty's review period, or resets it to the global default, and audits the change.
     *
     * @param facultyId the faculty
     * @param update    the new period, null to reset
     * @return the period after the change
     * @throws FacultyNotFoundException when the organisation is no faculty or outside the caller's dean scope
     * @throws ApiProblemException      422 {@code validation-failed} when the period is not a whole number from
     *                                  1 to 20
     */
    @Transactional
    public ReviewPeriodResponse update(long facultyId, ReviewPeriodUpdate update) {
        Organization faculty = faculty(facultyId);
        Authority authority = changeAuthority(facultyId).orElseThrow(() -> new FacultyNotFoundException(facultyId));
        Integer days = checked(update == null ? null : update.workingDays());
        if (!Objects.equals(days, faculty.getReviewWorkingDays())) {
            Map<String, Object> before = values(faculty);
            faculty.setReviewWorkingDays(days);
            Map<String, Object> after = values(faculty);
            if (authority.delegatorId() != null) {
                after.put("delegatorId", authority.delegatorId());
            }
            audit.recordChange(AuditAction.REVIEW_PERIOD_CHANGED, AuditEntityConstants.ORGANIZATIONS,
                access.callerId(), facultyId, before, after);
        }
        return response(faculty, true);
    }

    private Organization faculty(long facultyId) {
        return organizations.findById(facultyId)
            .filter(organization -> organization.getOrgType() == OrganizationType.FACULTY)
            .orElseThrow(() -> new FacultyNotFoundException(facultyId));
    }

    private boolean reads(long facultyId) {
        return access.scopes().stream()
            .anyMatch(scope -> READERS.contains(scope.role()) && tree.covers(scope.organizationId(), facultyId));
    }

    private Optional<Authority> changeAuthority(long facultyId) {
        if (access.has(SYSTEM_CONFIGURE)) {
            return Optional.of(new Authority(null));
        }
        Stream<Authority> held = access.heldScopes().stream()
            .filter(scope -> scope.role() == RoleType.DEAN && tree.covers(scope.organizationId(), facultyId))
            .map(scope -> new Authority(null));
        Stream<Authority> borrowed = access.delegations().stream()
            .filter(scope -> scope.role() == RoleType.DEAN && tree.covers(scope.organizationId(), facultyId))
            .map(scope -> new Authority(scope.delegatorId()));
        return Stream.concat(held, borrowed).findFirst();
    }

    private static Integer checked(BigDecimal days) {
        if (days == null) {
            return null;
        }
        if (days.stripTrailingZeros().scale() > 0 || days.compareTo(BigDecimal.valueOf(MIN_WORKING_DAYS)) < 0
            || days.compareTo(BigDecimal.valueOf(MAX_WORKING_DAYS)) > 0) {
            throw rangeViolation();
        }
        return days.intValueExact();
    }

    /**
     * The refusal of a period that is not a whole number from 1 to 20.
     *
     * @return 422 {@code validation-failed} naming {@code workingDays} with the code {@code range}
     */
    public static ApiProblemException rangeViolation() {
        return ApiProblemException.validationFailed("The review period is out of range", List.of(
            new FieldViolation(FIELD, "range",
                "Must be a whole number of working days from " + MIN_WORKING_DAYS + " to " + MAX_WORKING_DAYS)));
    }

    private Map<String, Object> values(Organization faculty) {
        Map<String, Object> values = new HashMap<>();
        values.put(FIELD, faculty.getReviewWorkingDays());
        values.put("effectiveWorkingDays", estimator.workingDays(faculty));
        return values;
    }

    private ReviewPeriodResponse response(Organization faculty, boolean updatable) {
        return new ReviewPeriodResponse(faculty.getId(), faculty.getReviewWorkingDays(),
            estimator.workingDays(faculty), estimator.defaultWorkingDays(), updatable);
    }

    private record Authority(Long delegatorId) {
    }
}
