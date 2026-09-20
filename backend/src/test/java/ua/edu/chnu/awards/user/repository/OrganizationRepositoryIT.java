package ua.edu.chnu.awards.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ua.edu.chnu.awards.support.AbstractJpaSliceTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;

class OrganizationRepositoryIT extends AbstractJpaSliceTest {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Test
    void listsActiveDepartmentsWithTheirFaculty() {
        List<Organization> departments =
            organizationRepository.findByOrgTypeAndActiveTrueOrderByName(OrganizationType.DEPARTMENT);

        assertThat(departments).isNotEmpty();
        assertThat(departments).allMatch(Organization::isActive);
        Organization dai = departments.stream()
            .filter(org -> org.getId().equals(TestUsers.DAI_DEPARTMENT_ID))
            .findFirst()
            .orElseThrow();
        assertThat(dai.getNameUk()).startsWith("Кафедра");
        assertThat(dai.getParent().getId()).isEqualTo(TestUsers.FMI_FACULTY_ID);
        assertThat(dai.getParent().getOrgType()).isEqualTo(OrganizationType.FACULTY);
        assertThat(dai.getDepth()).isEqualTo(2);
    }

    @Test
    void rootHasNoParent() {
        Organization university = organizationRepository.findById(TestUsers.UNIVERSITY_ID).orElseThrow();

        assertThat(university.getOrgType()).isEqualTo(OrganizationType.UNIVERSITY);
        assertThat(university.getParent()).isNull();
        assertThat(university.getCode()).isEqualTo("ChNU");
    }
}
