package ua.edu.chnu.awards.analytics.service;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
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
 * The rows comparing the child departments of a faculty, and the faculty level itself, which holds whatever was
 * submitted in the faculty outside its departments.
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
        Set<Long> subtree = tree.subtree(facultyId);
        List<Long> children = subtree.stream()
            .filter(id -> tree.node(id).filter(node -> node.type() == OrganizationType.DEPARTMENT
                && node.parentId() != null && node.parentId() == facultyId).isPresent())
            .toList();
        List<Long> named = new ArrayList<>(children);
        named.add(facultyId);
        Map<Long, Organization> units = organizations.findAllById(named).stream()
            .collect(Collectors.toMap(Organization::getId, Function.identity()));
        Set<Long> facultyLevel = new HashSet<>(subtree);
        List<UnitComparisonRow> rows = new ArrayList<>();
        for (Long child : children) {
            Set<Long> part = tree.subtree(child);
            facultyLevel.removeAll(part);
            UnitComparisonRow row = row(units.get(child), false, UnitFiguresHelper.in(part), figures, period,
                previous);
            if (row.active() || row.approved().current() + row.approved().previous() > 0) {
                rows.add(row);
            }
        }
        rows.sort(Comparator.comparing(row -> name(row.unit()), UKRAINIAN));
        UnitComparisonRow own = row(units.get(facultyId), true, UnitFiguresHelper.in(facultyLevel), figures,
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
