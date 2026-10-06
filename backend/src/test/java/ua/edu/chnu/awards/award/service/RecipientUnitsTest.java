package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.authz.OrganizationTree;
import ua.edu.chnu.awards.authz.RoleScope;
import ua.edu.chnu.awards.award.dto.UnitRef;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;

class RecipientUnitsTest {

    private static final long FACULTY = 9L;
    private static final long SPECIALITY = 30L;
    private static final long ALGEBRA = 64L;
    private static final long ANALYSIS = 65L;
    private static final long CLOSED = 66L;
    private static final long OTHER_FACULTY = 10L;

    private final AccessScope access = mock(AccessScope.class);
    private final OrganizationTree tree = mock(OrganizationTree.class);
    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final RecipientUnits units = new RecipientUnits(access, tree, organizations);
    private final Map<Long, Organization> rows = Map.of(
        FACULTY, TestUsers.organization(FACULTY, OrganizationType.FACULTY, "Faculty of Mathematics"),
        ALGEBRA, named(ALGEBRA, "Algebra", "Кафедра алгебри"),
        ANALYSIS, named(ANALYSIS, "Analysis", "Кафедра аналізу"));

    @BeforeEach
    void setUp() {
        node(FACULTY, OrganizationType.FACULTY, true);
        node(SPECIALITY, OrganizationType.SPECIALITY, true);
        node(ALGEBRA, OrganizationType.DEPARTMENT, true);
        node(ANALYSIS, OrganizationType.DEPARTMENT, true);
        node(CLOSED, OrganizationType.DEPARTMENT, false);
        node(OTHER_FACULTY, OrganizationType.FACULTY, true);
        when(tree.subtree(FACULTY)).thenReturn(Set.of(FACULTY, SPECIALITY, ALGEBRA, ANALYSIS, CLOSED));
        for (long id : List.of(FACULTY, SPECIALITY, ALGEBRA, ANALYSIS, CLOSED)) {
            when(tree.covers(FACULTY, id)).thenReturn(true);
        }
        when(organizations.findAllById(anyCollection())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().map(rows::get).toList();
        });
    }

    @Test
    void ac0_1_aSecretaryGetsHerFacultyFirstAndItsActiveDepartmentsAlphabetically() {
        when(access.scopes()).thenReturn(List.of(new RoleScope(RoleType.FACULTY_SECRETARY, FACULTY),
            new RoleScope(RoleType.EMPLOYEE, ALGEBRA)));

        assertThat(units.list()).extracting(UnitRef::id).containsExactly(FACULTY, ALGEBRA, ANALYSIS);
    }

    @Test
    void ac0_1_aDelegatedDeanRoleCountsLikeAnOwnOne() {
        when(access.scopes()).thenReturn(List.of(new RoleScope(RoleType.DEAN, FACULTY)));

        assertThat(units.covers(ANALYSIS)).isTrue();
    }

    @Test
    void ac0_1_anyoneWithoutEitherRoleGetsAnEmptyList() {
        when(access.scopes()).thenReturn(List.of(new RoleScope(RoleType.EMPLOYEE, ALGEBRA),
            new RoleScope(RoleType.RECTOR, TestUsers.UNIVERSITY_ID)));

        assertThat(units.list()).isEmpty();
        assertThat(units.covers(ALGEBRA)).isFalse();
    }

    @Test
    void ac0_3_unitsOutsideTheScopeOfOtherTypesOrInactiveAreNotCovered() {
        when(access.scopes()).thenReturn(List.of(new RoleScope(RoleType.FACULTY_SECRETARY, FACULTY)));

        assertThat(units.covers(OTHER_FACULTY)).isFalse();
        assertThat(units.covers(SPECIALITY)).isFalse();
        assertThat(units.covers(CLOSED)).isFalse();
        assertThat(units.covers(404L)).isFalse();
        assertThat(units.covers(FACULTY)).isTrue();
    }

    private void node(long id, OrganizationType type, boolean active) {
        when(tree.node(id)).thenReturn(Optional.of(new OrganizationTree.Node(id, type, null, 0, active)));
    }

    private static Organization named(long id, String name, String nameUk) {
        Organization organization = TestUsers.organization(id, OrganizationType.DEPARTMENT, name);
        organization.setNameUk(nameUk);
        return organization;
    }
}
