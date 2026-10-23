package ua.edu.chnu.awards.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.ARCHIVE;
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.CLOSED;
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.COLLEGE;
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.DAI;
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.DMA;
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.FMI;
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.HISTORY;
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.SPECIALITY;
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.UNIVERSITY;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ua.edu.chnu.awards.analytics.dto.AnalysableUnit;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.authz.RolePermissions;
import ua.edu.chnu.awards.authz.RoleScope;
import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.organization.OrganizationTree;
import ua.edu.chnu.awards.organization.entity.OrganizationType;
import ua.edu.chnu.awards.organization.service.OrganizationLookup;

@ExtendWith(MockitoExtension.class)
class UnitScopeTest {

    @Mock
    private AccessScope access;
    @Mock
    private OrganizationTree tree;
    @Mock
    private OrganizationLookup lookup;

    private UnitScope scope;

    @BeforeEach
    void setUp() {
        new UnitTreeFixture().stub(tree, lookup);
        scope = new UnitScope(access, new RolePermissions(), tree, lookup);
    }

    @Test
    void ac1_2_aFacultySecretaryListsTheActiveDepartmentsOfTheFacultyOnly() {
        holds(RoleType.FACULTY_SECRETARY, FMI);

        assertThat(scope.list()).extracting(AnalysableUnit::id).containsExactly(DAI, DMA);
        assertThat(scope.list().getFirst()).isEqualTo(new AnalysableUnit(DAI, "Algebra and Informatics",
            "Кафедра алгебри та інформатики", "C64", OrganizationType.DEPARTMENT, FMI));
    }

    @Test
    void ac1_2_aDeanListsTheFacultyFollowedByItsDepartments() {
        holds(RoleType.DEAN, FMI);

        assertThat(scope.list()).extracting(AnalysableUnit::id).containsExactly(FMI, DAI, DMA);
    }

    @Test
    void ac1_2_theRectorListsEveryFacultyAndDepartmentByUkrainianName() {
        holds(RoleType.RECTOR, UNIVERSITY);

        assertThat(scope.list()).extracting(AnalysableUnit::id).containsExactly(HISTORY, ARCHIVE, FMI, DAI, DMA)
            .doesNotContain(UNIVERSITY, COLLEGE, CLOSED, SPECIALITY);
    }

    @Test
    void ac1_2_rolesWithoutUnitAnalyticsAndBorrowedScopesListNothing() {
        when(access.heldScopes()).thenReturn(List.of(new RoleScope(RoleType.EMPLOYEE, FMI),
            new RoleScope(RoleType.GDPR_OFFICER, UNIVERSITY), new RoleScope(RoleType.SYSTEM_ADMIN, UNIVERSITY)));

        assertThat(scope.list()).isEmpty();
    }

    @Test
    void ac1_3_aUnitOutsideTheScopeOfAnotherTypeOrUnknownIsNotFound() {
        holds(RoleType.FACULTY_SECRETARY, FMI);

        assertThat(scope.require(DAI).id()).isEqualTo(DAI);
        assertThat(scope.require(CLOSED).type()).isEqualTo(OrganizationType.DEPARTMENT);
        for (long unit : new long[] {FMI, UNIVERSITY, ARCHIVE, COLLEGE, 999_999L}) {
            assertThatThrownBy(() -> scope.require(unit)).as(String.valueOf(unit))
                .isInstanceOf(UnitNotFoundException.class).hasMessage("Unit " + unit + " is not available");
        }
    }

    private void holds(RoleType role, long organizationId) {
        when(access.heldScopes()).thenReturn(List.of(new RoleScope(role, organizationId)));
    }
}
