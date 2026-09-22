package ua.edu.chnu.awards.authz;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ua.edu.chnu.awards.user.entity.RoleType;

class RoleLevelsTest {

    private final RoleLevels levels = new RoleLevels();

    @ParameterizedTest
    @CsvSource({
        "FACULTY_SECRETARY, EMPLOYEE, true",
        "DEAN, FACULTY_SECRETARY, true",
        "DEAN, EMPLOYEE, true",
        "RECTOR_SECRETARY, DEAN, true",
        "RECTOR, RECTOR_SECRETARY, true",
        "RECTOR, EMPLOYEE, true",
        "EMPLOYEE, EMPLOYEE, false",
        "DEAN, DEAN, false",
        "FACULTY_SECRETARY, DEAN, false",
        "DEAN, RECTOR, false",
        "EMPLOYEE, FACULTY_SECRETARY, false"
    })
    void ac14_theLineOfAuthorityIsStrict(RoleType holder, RoleType target, boolean expected) {
        assertThat(levels.above(holder, target)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "SYSTEM_ADMIN, EMPLOYEE, true",
        "SYSTEM_ADMIN, RECTOR, true",
        "SYSTEM_ADMIN, SYSTEM_ADMIN, true",
        "SYSTEM_ADMIN, GDPR_OFFICER, true",
        "RECTOR, SYSTEM_ADMIN, false",
        "RECTOR, GDPR_OFFICER, false",
        "GDPR_OFFICER, EMPLOYEE, false",
        "GDPR_OFFICER, GDPR_OFFICER, false",
        "DEAN, GDPR_OFFICER, false"
    })
    void ac14_systemRolesAreGrantedByTheAdministratorOnly(RoleType holder, RoleType target, boolean expected) {
        assertThat(levels.above(holder, target)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "EMPLOYEE, 0", "FACULTY_SECRETARY, 1", "DEAN, 2", "RECTOR_SECRETARY, 3", "RECTOR, 4"
    })
    void ac14_lineRolesAreOrdered(RoleType role, int level) {
        assertThat(levels.of(role)).isEqualTo(level);
    }

    @ParameterizedTest
    @CsvSource({
        "SYSTEM_ADMIN, true", "GDPR_OFFICER, true", "RECTOR, false", "EMPLOYEE, false"
    })
    void ac14_systemRolesAreOutsideTheLine(RoleType role, boolean system) {
        assertThat(levels.isSystem(role)).isEqualTo(system);
    }
}
