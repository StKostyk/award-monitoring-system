package ua.edu.chnu.awards.authz;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.OrganizationType;

class OrganizationTreeIT extends AbstractIntegrationTest {

    @Autowired
    private OrganizationTree tree;

    @Test
    void ac12_theSeededHierarchyIsLoadedAtStart() {
        assertThat(tree.covers(TestUsers.UNIVERSITY_ID, TestUsers.DAI_DEPARTMENT_ID)).isTrue();
        assertThat(tree.covers(TestUsers.FMI_FACULTY_ID, TestUsers.DAI_DEPARTMENT_ID)).isTrue();
        assertThat(tree.covers(TestUsers.DAI_DEPARTMENT_ID, TestUsers.FMI_FACULTY_ID)).isFalse();
        assertThat(tree.subtree(TestUsers.UNIVERSITY_ID)).hasSizeGreaterThan(50);
        assertThat(tree.node(TestUsers.DAI_DEPARTMENT_ID)).hasValueSatisfying(node -> {
            assertThat(node.type()).isEqualTo(OrganizationType.DEPARTMENT);
            assertThat(node.parentId()).isEqualTo(TestUsers.FMI_FACULTY_ID);
            assertThat(node.depth()).isEqualTo(2);
        });
    }
}
