package ua.edu.chnu.awards.analytics.service;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongPredicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.analytics.dto.Figure;
import ua.edu.chnu.awards.analytics.dto.UnitComparisonRow;
import ua.edu.chnu.awards.analytics.dto.UnitFigures;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.organization.OrganizationTree;
import ua.edu.chnu.awards.organization.dto.OrganizationRef;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.entity.OrganizationType;
import ua.edu.chnu.awards.organization.service.OrganizationLookup;

import lombok.RequiredArgsConstructor;

/**
 * The rows comparing the child units of a parent, the departments of a faculty or the faculties and colleges of the
 * university, and the parent level itself, which holds whatever was submitted in the parent outside its children.
 */
@Component
@RequiredArgsConstructor
public class UnitComparison {

    private static final Collator UKRAINIAN = Collator.getInstance(Locale.forLanguageTag("uk"));

    private final OrganizationTree tree;
    private final OrganizationLookup organizations;

    /**
     * The rows of a faculty: every active child department, an inactive one only with counted awards, and the
     * faculty level when it has members or counted awards; departments by Ukrainian name, the faculty level last.
     *
     * @param facultyId the faculty
     * @param figures   the aggregates of the faculty
     * @param period    the calendar years
     * @param previous  the previous period
     * @return the rows, whose counts add up to the faculty's
     */
    public List<UnitComparisonRow> rows(long facultyId, UnitFigures figures, YearRange period,
                                        YearRange previous) {
        return rows(facultyId, EnumSet.of(OrganizationType.DEPARTMENT), figures, period, previous);
    }

    /**
     * The rows of a parent: every active direct child of the given types, an inactive one only with counted awards,
     * and the parent level when it has members or counted awards; children by Ukrainian name, the parent level
     * last.
     *
     * @param parentId   the parent
     * @param childTypes the types of the children compared
     * @param figures    the aggregates of the parent
     * @param period     the calendar years
     * @param previous   the previous period
     * @return the rows, whose counts add up to the parent's
     */
    public List<UnitComparisonRow> rows(long parentId, Set<OrganizationType> childTypes, UnitFigures figures,
                                        YearRange period, YearRange previous) {
        Set<Long> subtree = tree.subtree(parentId);
        List<Long> children = subtree.stream()
            .filter(id -> tree.node(id).filter(node -> childTypes.contains(node.type())
                && node.parentId() != null && node.parentId() == parentId).isPresent())
            .toList();
        List<Long> named = new ArrayList<>(children);
        named.add(parentId);
        Map<Long, Organization> units = organizations.findAllById(named).stream()
            .collect(Collectors.toMap(Organization::getId, Function.identity()));
        Set<Long> parentLevel = new HashSet<>(subtree);
        List<UnitComparisonRow> rows = new ArrayList<>();
        for (Long child : children) {
            Set<Long> part = tree.subtree(child);
            parentLevel.removeAll(part);
            UnitComparisonRow row = row(units.get(child), false, UnitFiguresHelper.in(part), figures, period,
                previous);
            if (row.active() || row.approved().current() + row.approved().previous() > 0) {
                rows.add(row);
            }
        }
        rows.sort(Comparator.comparing(row -> name(row.unit()), UKRAINIAN));
        UnitComparisonRow own = row(units.get(parentId), true, UnitFiguresHelper.in(parentLevel), figures,
            period, previous);
        if (own.members() > 0 || own.approved().current() + own.approved().previous() > 0) {
            rows.add(own);
        }
        return rows;
    }

    private static UnitComparisonRow row(Organization unit, boolean facultyLevel, LongPredicate part,
                                         UnitFigures figures, YearRange period, YearRange previous) {
        long approved = UnitFiguresHelper.approved(figures, part, period);
        long members = UnitFiguresHelper.members(figures, part);
        return new UnitComparisonRow(new OrganizationRef(unit.getId(), unit.getName(), unit.getNameUk(),
            unit.getCode(), unit.getOrgType()), facultyLevel, unit.isActive(), members,
            Figure.of(approved, UnitFiguresHelper.approved(figures, part, previous)),
            UnitFiguresHelper.awardedPeople(figures, part, true), UnitFiguresHelper.perHundred(approved, members),
            UnitFiguresHelper.verified(figures, part, period), UnitFiguresHelper.yearSeries(figures, part, period));
    }

    private static String name(OrganizationRef unit) {
        return unit.nameUk() == null ? unit.name() : unit.nameUk();
    }
}
