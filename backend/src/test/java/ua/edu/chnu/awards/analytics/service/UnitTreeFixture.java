package ua.edu.chnu.awards.analytics.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import ua.edu.chnu.awards.organization.OrganizationTree;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.entity.OrganizationType;
import ua.edu.chnu.awards.organization.service.OrganizationLookup;

/**
 * A small organisation tree behind mocked {@link OrganizationTree} and {@link OrganizationLookup}: the university,
 * two faculties with departments, a college, and two inactive departments of the first faculty.
 */
final class UnitTreeFixture {

    static final long UNIVERSITY = 1L;
    static final long FMI = 9L;
    static final long HISTORY = 12L;
    static final long COLLEGE = 30L;
    static final long DAI = 64L;
    static final long DMA = 66L;
    static final long CLOSED = 70L;
    static final long CLOSED_WITH_AWARDS = 71L;
    static final long ARCHIVE = 80L;
    static final long SPECIALITY = 90L;

    private final Map<Long, Organization> organizations = new LinkedHashMap<>();
    private final Map<Long, OrganizationTree.Node> nodes = new LinkedHashMap<>();

    UnitTreeFixture() {
        add(UNIVERSITY, null, OrganizationType.UNIVERSITY, "University", "Університет", true);
        add(FMI, UNIVERSITY, OrganizationType.FACULTY, "Faculty of Mathematics", "Факультет математики", true);
        add(HISTORY, UNIVERSITY, OrganizationType.FACULTY, "Faculty of History", "Історичний факультет", true);
        add(COLLEGE, UNIVERSITY, OrganizationType.COLLEGE, "College", "Коледж", true);
        add(DMA, FMI, OrganizationType.DEPARTMENT, "Mathematical Analysis", "Кафедра математичного аналізу", true);
        add(DAI, FMI, OrganizationType.DEPARTMENT, "Algebra and Informatics", "Кафедра алгебри та інформатики",
            true);
        add(CLOSED, FMI, OrganizationType.DEPARTMENT, "Closed", "Кафедра закрита", false);
        add(CLOSED_WITH_AWARDS, FMI, OrganizationType.DEPARTMENT, "Geometry", "Кафедра геометрії", false);
        add(ARCHIVE, HISTORY, OrganizationType.DEPARTMENT, "Archives", "Кафедра архівознавства", true);
        add(SPECIALITY, DAI, OrganizationType.SPECIALITY, "Computer Science", "Комп'ютерні науки", true);
    }

    void stub(OrganizationTree tree, OrganizationLookup lookup) {
        lenient().when(tree.node(anyLong()))
            .thenAnswer(call -> Optional.ofNullable(nodes.get(call.<Long>getArgument(0))));
        lenient().when(tree.subtree(anyLong())).thenAnswer(call -> subtree(call.getArgument(0)));
        lenient().when(tree.university()).thenReturn(Optional.of(nodes.get(UNIVERSITY)));
        lenient().when(lookup.findById(any())).thenAnswer(call -> Optional.ofNullable(organizations.get(
            call.<Long>getArgument(0))));
        lenient().when(lookup.findAllById(any())).thenAnswer(call -> {
            List<Organization> found = new ArrayList<>();
            call.<Iterable<Long>>getArgument(0).forEach(id -> Optional.ofNullable(organizations.get(id))
                .ifPresent(found::add));
            return found;
        });
    }

    Organization organization(long id) {
        return organizations.get(id);
    }

    private Set<Long> subtree(long root) {
        Set<Long> result = new HashSet<>();
        if (nodes.containsKey(root)) {
            result.add(root);
            nodes.values().stream().filter(node -> node.parentId() != null && node.parentId() == root)
                .forEach(node -> result.addAll(subtree(node.id())));
        }
        return result;
    }

    private void add(long id, Long parent, OrganizationType type, String name, String nameUk, boolean active) {
        organizations.put(id, Organization.builder().id(id).name(name).nameUk(nameUk).code("C" + id).orgType(type)
            .active(active).build());
        nodes.put(id, new OrganizationTree.Node(id, type, parent, parent == null ? 0 : 1, active));
    }
}
