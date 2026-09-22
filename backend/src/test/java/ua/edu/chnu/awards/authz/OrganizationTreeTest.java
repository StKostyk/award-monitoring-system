package ua.edu.chnu.awards.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;

class OrganizationTreeTest {

    private final OrganizationRepository repository = mock(OrganizationRepository.class);
    private final OrganizationTree tree = new OrganizationTree(repository);

    private final Organization university = org(1L, OrganizationType.UNIVERSITY, null, true);
    private final Organization fmi = org(9L, OrganizationType.FACULTY, university, true);
    private final Organization fppsw = org(10L, OrganizationType.FACULTY, university, true);
    private final Organization dai = org(64L, OrganizationType.DEPARTMENT, fmi, true);
    private final Organization closed = org(65L, OrganizationType.DEPARTMENT, fmi, false);

    @BeforeEach
    void load() {
        when(repository.findAll()).thenReturn(List.of(university, fmi, fppsw, dai, closed));
        tree.refresh();
    }

    @Test
    void ac13_aScopeCoversItselfAndEveryDescendant() {
        assertThat(tree.covers(9L, 64L)).isTrue();
        assertThat(tree.covers(9L, 9L)).isTrue();
        assertThat(tree.covers(1L, 64L)).isTrue();
        assertThat(tree.covers(9L, 10L)).isFalse();
        assertThat(tree.covers(64L, 9L)).isFalse();
        assertThat(tree.covers(9L, 999L)).isFalse();
    }

    @Test
    void ac12_subtreeListsIdsIncludingInactiveOnes() {
        assertThat(tree.subtree(9L)).containsExactlyInAnyOrder(9L, 64L, 65L);
        assertThat(tree.subtree(1L)).containsExactlyInAnyOrder(1L, 9L, 10L, 64L, 65L);
        assertThat(tree.subtree(999L)).isEmpty();
    }

    @Test
    void ac12_nodesExposeTypeAndActivity() {
        assertThat(tree.node(64L)).hasValueSatisfying(node -> {
            assertThat(node.type()).isEqualTo(OrganizationType.DEPARTMENT);
            assertThat(node.parentId()).isEqualTo(9L);
            assertThat(node.active()).isTrue();
        });
        assertThat(tree.node(65L)).hasValueSatisfying(node -> assertThat(node.active()).isFalse());
        assertThat(tree.node(999L)).isEmpty();
    }

    @Test
    void ac12_refreshReplacesTheTreeAtomically() {
        Organization newFaculty = org(11L, OrganizationType.FACULTY, university, true);
        when(repository.findAll()).thenReturn(List.of(university, newFaculty));

        tree.refresh();

        assertThat(tree.subtree(1L)).isEqualTo(Set.of(1L, 11L));
        assertThat(tree.covers(9L, 64L)).isFalse();
        verify(repository, org.mockito.Mockito.times(2)).findAll();
    }

    @Test
    void ac13_aParentCycleIsOutsideEveryScopeInsteadOfHangingTheCheck() {
        Organization a = org(100L, OrganizationType.FACULTY, null, true);
        Organization b = org(101L, OrganizationType.DEPARTMENT, a, true);
        a.setParent(b);
        when(repository.findAll()).thenReturn(List.of(university, a, b));
        tree.refresh();

        assertThat(tree.covers(1L, 101L)).isFalse();
        assertThat(tree.covers(100L, 101L)).isTrue();
        assertThat(tree.subtree(1L)).containsExactly(1L);
    }

    private static Organization org(Long id, OrganizationType type, Organization parent, boolean active) {
        return Organization.builder().id(id).orgType(type).parent(parent).active(active)
            .depth(parent == null ? 0 : parent.getDepth() + 1).build();
    }
}
