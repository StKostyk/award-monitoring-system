package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.user.entity.RoleType;

class RolePermissionsTest {

    private final RolePermissions permissions = new RolePermissions();

    @Test
    void ac14_everyRoleHasPermissionsFromTheDesignMatrix() {
        for (RoleType role : RoleType.values()) {
            assertThat(permissions.of(role)).as(role.name()).isNotEmpty();
        }
        assertThat(permissions.of(RoleType.EMPLOYEE)).containsExactlyInAnyOrder(
            "award:read:own", "award:create", "award:update:own");
        assertThat(permissions.of(RoleType.RECTOR)).contains("award:approve:final");
        assertThat(permissions.of(RoleType.SYSTEM_ADMIN)).contains("user:manage", "system:configure")
            .doesNotContain("award:create");
        assertThat(permissions.of(RoleType.GDPR_OFFICER)).contains("consent:manage")
            .doesNotContain("user:manage");
    }

    @Test
    void ac14_unionIsSortedAndDeduplicated() {
        List<String> union = permissions.union(List.of(RoleType.EMPLOYEE, RoleType.FACULTY_SECRETARY));

        assertThat(union).isSorted().doesNotHaveDuplicates()
            .contains("award:approve:level1", "award:read:department", "award:read:own");
        assertThat(permissions.union(List.of())).isEmpty();
    }
}
