package ua.edu.chnu.awards.analytics.export;

import static ua.edu.chnu.awards.analytics.export.ReportLabels.APPROVED;
import static ua.edu.chnu.awards.analytics.export.ReportLabels.AWARDED_PEOPLE;
import static ua.edu.chnu.awards.analytics.export.ReportLabels.MEMBERS;
import static ua.edu.chnu.awards.analytics.export.ReportLabels.PER_HUNDRED;
import static ua.edu.chnu.awards.analytics.export.ReportLabels.VERIFIED;

import java.util.ArrayList;
import java.util.List;

import ua.edu.chnu.awards.analytics.dto.TopPerformers;
import ua.edu.chnu.awards.analytics.dto.UnitComparisonRow;
import ua.edu.chnu.awards.analytics.dto.UnitHighlight;
import ua.edu.chnu.awards.organization.dto.OrganizationRef;

/**
 * The rows of the sections that compare departments and name people.
 */
final class UnitReportRowsHelper {

    private UnitReportRowsHelper() {
    }

    static List<UnitReport.Row> departments(List<UnitComparisonRow> units, int firstYear, ReportLabels labels) {
        List<UnitReport.Row> rows = new ArrayList<>();
        for (UnitComparisonRow unit : units) {
            String code = code(unit.unit()) + ".";
            String name = unitName(unit, labels);
            rows.add(UnitReport.Row.count(code + MEMBERS, row(labels, name, MEMBERS), unit.members()));
            rows.add(UnitReport.Row.counts(code + APPROVED, row(labels, name, APPROVED), unit.approved().current(),
                unit.approved().previous()));
            rows.add(UnitReport.Row.count(code + AWARDED_PEOPLE, row(labels, name, AWARDED_PEOPLE),
                unit.awardedPeople()));
            rows.add(UnitReport.Row.figure(code + PER_HUNDRED, row(labels, name, PER_HUNDRED),
                unit.perHundredMembers()));
            rows.add(UnitReport.Row.count(code + VERIFIED, row(labels, name, VERIFIED), unit.verified()));
            String byYear = labels.text("departments.row", name, labels.text("departments.year"));
            for (int i = 0; i < unit.byYear().size(); i++) {
                rows.add(UnitReport.Row.inYear(code + "byYear", byYear, firstYear + i, unit.byYear().get(i)));
            }
        }
        return rows;
    }

    static List<UnitReport.Row> topPerformers(TopPerformers top, ReportLabels labels) {
        if (top.status() == TopPerformers.Status.GROUP_TOO_SMALL) {
            return List.of(UnitReport.Row.text("groupTooSmall", labels.text("top.groupTooSmall")));
        }
        if (top.people().isEmpty()) {
            return List.of(UnitReport.Row.text("empty", labels.text("top.empty")));
        }
        List<UnitReport.Row> rows = new ArrayList<>();
        for (int i = 0; i < top.people().size(); i++) {
            TopPerformers.Person person = top.people().get(i);
            StringBuilder label = new StringBuilder(person.lastName()).append(' ').append(person.firstName());
            if (person.department() != null) {
                label.append(", ").append(labels.name(person.department()));
            }
            if (person.highestLevel() != null) {
                label.append(", ").append(labels.text("top.highest")).append(": ")
                    .append(labels.level(person.highestLevel()));
            }
            rows.add(UnitReport.Row.count(String.valueOf(i + 1), label.toString(), person.count()));
        }
        return rows;
    }

    static List<UnitReport.Row> highlights(List<UnitHighlight> highlights, ReportLabels labels) {
        return highlights.stream().map(highlight -> {
            String type = highlight.type() == UnitHighlight.Type.NO_AWARDS && highlight.previous() == 0
                ? "NO_AWARDS_EVER" : highlight.type().name();
            String kind = labels.text(highlight.type() == UnitHighlight.Type.GROWTH
                ? "highlights.strength" : "highlights.gap");
            String text = labels.text("highlights." + type, labels.name(highlight.unit()),
                String.valueOf(highlight.current()), String.valueOf(highlight.previous()));
            return UnitReport.Row.counts(type, kind + ": " + text, highlight.current(), highlight.previous());
        }).toList();
    }

    private static String row(ReportLabels labels, String unit, String figure) {
        return labels.text("departments.row", unit, labels.figure(figure));
    }

    private static String unitName(UnitComparisonRow unit, ReportLabels labels) {
        if (unit.facultyLevel()) {
            return labels.text("departments.facultyLevel");
        }
        String name = labels.name(unit.unit());
        return unit.active() ? name : labels.text("departments.inactive", name);
    }

    private static String code(OrganizationRef unit) {
        return unit.code() == null || unit.code().isBlank() ? String.valueOf(unit.id()) : unit.code();
    }
}
