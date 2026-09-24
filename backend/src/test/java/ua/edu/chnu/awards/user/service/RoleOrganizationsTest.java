package ua.edu.chnu.awards.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;

class RoleOrganizationsTest {

    private final RoleOrganizations organizations = new RoleOrganizations();

    @ParameterizedTest
    @CsvSource({
        "EMPLOYEE, DEPARTMENT, true",
        "EMPLOYEE, FACULTY, false",
        "EMPLOYEE, UNIVERSITY, false",
        "FACULTY_SECRETARY, FACULTY, true",
        "FACULTY_SECRETARY, DEPARTMENT, true",
        "FACULTY_SECRETARY, UNIVERSITY, false",
        "DEAN, FACULTY, true",
        "DEAN, COLLEGE, true",
        "DEAN, DEPARTMENT, false",
        "RECTOR, UNIVERSITY, true",
        "RECTOR, FACULTY, false",
        "RECTOR_SECRETARY, UNIVERSITY, true",
        "RECTOR_SECRETARY, DEPARTMENT, false",
        "SYSTEM_ADMIN, UNIVERSITY, true",
        "SYSTEM_ADMIN, FACULTY, false",
        "GDPR_OFFICER, UNIVERSITY, true",
        "GDPR_OFFICER, SPECIALITY, false"})
    void ac2_3_aRoleFitsOnlyItsLevelOfTheTree(RoleType role, OrganizationType type, boolean expected) {
        assertThat(organizations.fits(role, type)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"EMPLOYEE, DEPARTMENT", "DEAN, COLLEGE", "RECTOR, UNIVERSITY"})
    void ac2_3_theRefusalCanNameTheLevelsThatWouldFit(RoleType role, OrganizationType type) {
        assertThat(organizations.levelsOf(role)).contains(type);
    }
}
