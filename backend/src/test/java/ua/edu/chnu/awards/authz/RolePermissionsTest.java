package ua.edu.chnu.awards.authz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RolePermissionsTest {

    private final RolePermissions permissions = new RolePermissions();

    @Test
    void ac14_everyRoleHasPermissionsFromTheDesignMatrix() {
        for (RoleType role : RoleType.values()) {
            assertThat(permissions.of(role)).as(role.name()).isNotEmpty();
        }
        assertThat(permissions.of(RoleType.EMPLOYEE)).containsExactlyInAnyOrder(
            "award:read:own", "award:create", "award:update:own", "analytics:read:own");
        assertThat(permissions.of(RoleType.RECTOR)).contains("award:approve:final");
        assertThat(permissions.of(RoleType.SYSTEM_ADMIN)).contains("user:manage", "system:configure")
            .doesNotContain("award:create");
        assertThat(permissions.of(RoleType.GDPR_OFFICER)).contains("consent:manage")
            .doesNotContain("user:manage");
    }

    @Test
    void ac11_approvalRolesReadAndManageUsersInsideTheirScopeOnly() {
        for (RoleType role : List.of(RoleType.FACULTY_SECRETARY, RoleType.DEAN, RoleType.RECTOR_SECRETARY,
            RoleType.RECTOR)) {
            assertThat(permissions.of(role)).as(role.name()).contains("user:read:scope", "user:manage:scope")
                .doesNotContain("user:read:all", "user:manage");
        }
        assertThat(permissions.of(RoleType.EMPLOYEE)).doesNotContain("user:read:scope", "user:manage:scope");
        assertThat(permissions.of(RoleType.SYSTEM_ADMIN)).contains("user:read:all", "user:manage")
            .doesNotContain("user:manage:scope");
    }

    @Test
    void ac4_theAdministratorReadsTheAuditTrailButNoAwardsAndTheGdprOfficerReadsBoth() {
        assertThat(permissions.of(RoleType.SYSTEM_ADMIN)).contains("audit:read", "award:read:own")
            .doesNotContain("award:read:department", "award:read:faculty", "award:read:all");
        assertThat(permissions.of(RoleType.GDPR_OFFICER)).contains("audit:read", "award:read:department",
            "award:read:faculty", "award:read:all");
    }

    @Test
    void ac3_1_onlyTheGdprOfficerManagesLegalHolds() {
        for (RoleType role : RoleType.values()) {
            assertThat(permissions.of(role).contains("legal-hold:manage")).as(role.name())
                .isEqualTo(role == RoleType.GDPR_OFFICER);
        }
    }

    @Test
    void ac1_1_unitAnalyticsBelongToTheApproversAndAreNeverLent() {
        Set<RoleType> departments = EnumSet.of(RoleType.FACULTY_SECRETARY, RoleType.DEAN, RoleType.RECTOR_SECRETARY,
            RoleType.RECTOR);
        Set<RoleType> faculties = EnumSet.of(RoleType.DEAN, RoleType.RECTOR_SECRETARY, RoleType.RECTOR);
        for (RoleType role : RoleType.values()) {
            assertThat(permissions.of(role).contains("analytics:read:department")).as(role.name())
                .isEqualTo(departments.contains(role));
            assertThat(permissions.of(role).contains("analytics:read:faculty")).as(role.name())
                .isEqualTo(faculties.contains(role));
            assertThat(permissions.delegableOf(role)).as(role.name())
                .doesNotContain("analytics:read:department", "analytics:read:faculty");
        }
    }

    @Test
    void ac1_1_universityAnalyticsBelongToTheRectorAndTheSecretaryAndAreNeverLent() {
        Set<RoleType> holders = EnumSet.of(RoleType.RECTOR_SECRETARY, RoleType.RECTOR);
        for (RoleType role : RoleType.values()) {
            assertThat(permissions.of(role).contains("analytics:read:university")).as(role.name())
                .isEqualTo(holders.contains(role));
            assertThat(permissions.delegableOf(role)).as(role.name()).doesNotContain("analytics:read:university");
        }
    }

    @Test
    void ac2_1_onlyTheGdprOfficerReadsTheCompliancePage() {
        for (RoleType role : RoleType.values()) {
            assertThat(permissions.of(role).contains("compliance:read")).as(role.name())
                .isEqualTo(role == RoleType.GDPR_OFFICER);
        }
    }

    @Test
    void ac14_unionIsSortedAndDeduplicated() {
        List<String> union = permissions.union(List.of(RoleType.EMPLOYEE, RoleType.FACULTY_SECRETARY));

        assertThat(union).isSorted().doesNotHaveDuplicates()
            .contains("award:approve:level1", "award:read:department", "award:read:own");
        assertThat(permissions.union(List.of())).isEmpty();
    }
}
