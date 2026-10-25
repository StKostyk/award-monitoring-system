package ua.edu.chnu.awards.analytics.export;

import static ua.edu.chnu.awards.analytics.export.BoardFormatHelper.change;
import static ua.edu.chnu.awards.analytics.export.BoardFormatHelper.decimal;
import static ua.edu.chnu.awards.analytics.export.BoardReportAssembler.page;
import static ua.edu.chnu.awards.analytics.export.ReportLabels.APPROVED;
import static ua.edu.chnu.awards.analytics.export.ReportLabels.AWARDED_PEOPLE;
import static ua.edu.chnu.awards.analytics.export.ReportLabels.MEMBERS;
import static ua.edu.chnu.awards.analytics.export.ReportLabels.PERSONAL;
import static ua.edu.chnu.awards.analytics.export.ReportLabels.PER_HUNDRED;
import static ua.edu.chnu.awards.analytics.export.ReportLabels.UNIT_AWARDS;
import static ua.edu.chnu.awards.analytics.export.ReportLabels.VERIFIED;

import java.math.BigDecimal;
import java.util.List;

import ua.edu.chnu.awards.analytics.dto.Figure;
import ua.edu.chnu.awards.analytics.dto.TrendProjection;
import ua.edu.chnu.awards.analytics.dto.UnitComparisonRow;
import ua.edu.chnu.awards.analytics.dto.UnitTotals;
import ua.edu.chnu.awards.analytics.dto.UniversityAnalytics;

/**
 * The pages of the board report up to the faculty comparison.
 */
final class BoardFiguresHelper {

    private BoardFiguresHelper() {
    }

    static BoardReport.Page figures(UnitTotals totals, ReportLabels labels) {
        List<BoardReport.Row> rows = List.of(
            figureRow(APPROVED, totals.approved(), labels),
            figureRow(AWARDED_PEOPLE, totals.awardedPeople(), labels),
            single(PERSONAL, String.valueOf(totals.personal()), labels),
            single(UNIT_AWARDS, String.valueOf(totals.unitAwards()), labels),
            single(VERIFIED, String.valueOf(totals.verified()), labels),
            single(MEMBERS, String.valueOf(totals.members()), labels),
            single(PER_HUNDRED, decimal(totals.perHundredMembers()), labels));
        BoardReport.Table table = new BoardReport.Table(null, List.of(labels.text("board.column.figure"),
            labels.text("column.current"), labels.text("column.previous"), labels.text("board.column.change")),
            rows, labels.text("empty"));
        return page("figures", labels, List.of(), List.of(table), List.of());
    }

    static BoardReport.Page trend(UniversityAnalytics analytics, ReportLabels labels) {
        List<BoardReport.Row> rows = analytics.byYear().stream().map(year -> new BoardReport.Row(
            String.valueOf(year.year()), List.of(String.valueOf(year.total()), String.valueOf(year.personal()),
                String.valueOf(year.unitAwards())))).toList();
        BoardReport.Table table = new BoardReport.Table(null, List.of(labels.text("column.year"),
            labels.figure(APPROVED), labels.figure(PERSONAL), labels.figure(UNIT_AWARDS)), rows,
            labels.text("empty"));
        BoardReport.Chart chart = new BoardReport.Chart(labels.text("board.chart.byYear"),
            BoardFormatHelper.bars(analytics.byYear(), year -> String.valueOf(year.year()),
                year -> BigDecimal.valueOf(year.total())));
        TrendProjection projection = analytics.projection();
        String sentence = projection.status() == TrendProjection.Status.PROJECTED
            ? labels.text("board.projection", String.valueOf(projection.year()),
                String.valueOf(projection.estimate()), String.valueOf(projection.basis().from()),
                String.valueOf(projection.basis().to()))
            : labels.text("board.projection.tooFew");
        return page("trend", labels, List.of(sentence), List.of(table), List.of(chart));
    }

    static BoardReport.Page comparison(List<UnitComparisonRow> units, ReportLabels labels) {
        List<BoardReport.Row> rows = units.stream().map(unit -> new BoardReport.Row(unitName(unit, labels),
            List.of(String.valueOf(unit.members()), String.valueOf(unit.approved().current()),
                String.valueOf(unit.approved().previous()), change(unit.approved()),
                decimal(unit.perHundredMembers()), String.valueOf(unit.verified())))).toList();
        BoardReport.Table table = new BoardReport.Table(null, List.of(labels.text("board.column.unit"),
            labels.figure(MEMBERS), labels.text("column.current"), labels.text("column.previous"),
            labels.text("board.column.change"), labels.figure(PER_HUNDRED), labels.figure(VERIFIED)), rows,
            labels.text("empty"));
        List<UnitComparisonRow> measured = units.stream().filter(unit -> unit.perHundredMembers() != null).toList();
        BoardReport.Chart chart = new BoardReport.Chart(labels.figure(PER_HUNDRED),
            BoardFormatHelper.bars(measured, unit -> unitName(unit, labels), UnitComparisonRow::perHundredMembers));
        return page("comparison", labels, List.of(), List.of(table), List.of(chart));
    }

    private static BoardReport.Row figureRow(String key, Figure figure, ReportLabels labels) {
        return new BoardReport.Row(labels.figure(key), BoardFormatHelper.counts(figure.current(), figure.previous(),
            figure.delta(), figure.deltaPercent()));
    }

    private static BoardReport.Row single(String key, String value, ReportLabels labels) {
        return new BoardReport.Row(labels.figure(key), List.of(value, "", ""));
    }

    private static String unitName(UnitComparisonRow unit, ReportLabels labels) {
        if (unit.facultyLevel()) {
            return labels.text("board.universityLevel");
        }
        String name = labels.name(unit.unit());
        return unit.active() ? name : labels.text("departments.inactive", name);
    }
}
