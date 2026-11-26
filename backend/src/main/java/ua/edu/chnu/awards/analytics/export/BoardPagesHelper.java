package ua.edu.chnu.awards.analytics.export;

import static ua.edu.chnu.awards.analytics.export.BoardFormatHelper.counts;
import static ua.edu.chnu.awards.analytics.export.BoardReportAssembler.TIME;
import static ua.edu.chnu.awards.analytics.export.BoardReportAssembler.page;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import ua.edu.chnu.awards.analytics.dto.AnalyticsTrends;
import ua.edu.chnu.awards.analytics.dto.CategoryTrend;
import ua.edu.chnu.awards.analytics.dto.ComplianceSummary;
import ua.edu.chnu.awards.analytics.dto.SourceTrend;
import ua.edu.chnu.awards.analytics.dto.UnitHighlight;
import ua.edu.chnu.awards.analytics.dto.UniversityAnalytics;
import ua.edu.chnu.awards.gdpr.ComplianceFigures;

/**
 * The pages of the board report after the faculty comparison.
 */
final class BoardPagesHelper {

    private static final String EMPTY = "empty";
    private static final String CURRENT = "column.current";
    private static final String PREVIOUS = "column.previous";
    private static final String CHANGE = "board.column.change";
    private static final Set<UnitHighlight.Type> STRENGTHS = Set.of(UnitHighlight.Type.GROWTH,
        UnitHighlight.Type.ABOVE_AVERAGE);
    private static final Set<UnitHighlight.Type> UNIVERSITY_WORDING = Set.of(UnitHighlight.Type.BELOW_AVERAGE,
        UnitHighlight.Type.ABOVE_AVERAGE);

    private BoardPagesHelper() {
    }

    static BoardReport.Page categories(UniversityAnalytics analytics, ReportLabels labels) {
        BoardReport.Table byCategory = comparisonTable("section.byCategory", "board.column.category",
            analytics.byCategory().stream().map(row -> new BoardReport.Row(
                labels.language().name(row.category().name(), row.category().nameUk()),
                counts(row.count(), row.previous()))).toList(), labels.text(EMPTY), labels);
        BoardReport.Table byLevel = comparisonTable("section.byLevel", "board.column.level",
            analytics.byLevel().stream().map(row -> new BoardReport.Row(labels.level(row.level()),
                counts(row.count(), row.previous()))).toList(), labels.text(EMPTY), labels);
        return page("categories", labels, List.of(), List.of(byCategory, byLevel), List.of());
    }

    static BoardReport.Page trends(AnalyticsTrends trends, ReportLabels labels) {
        String noCategory = labels.text("board.trends.noCategory");
        String noSource = labels.text("board.trends.noSource");
        return page("trends", labels, List.of(), List.of(
            comparisonTable("board.trends.risingCategories", "board.column.category",
                categoryRows(trends.categories().rising(), labels), noCategory, labels),
            comparisonTable("board.trends.fallingCategories", "board.column.category",
                categoryRows(trends.categories().falling(), labels), noCategory, labels),
            comparisonTable("board.trends.risingSources", "board.column.source",
                sourceRows(trends.sources().rising()), noSource, labels),
            comparisonTable("board.trends.fallingSources", "board.column.source",
                sourceRows(trends.sources().falling()), noSource, labels)), List.of());
    }

    static BoardReport.Page highlights(List<UnitHighlight> highlights, ReportLabels labels) {
        if (highlights.isEmpty()) {
            return page("highlights", labels, List.of(labels.text("board.highlights.none")), List.of(), List.of());
        }
        List<BoardReport.Row> strengths = new ArrayList<>();
        List<BoardReport.Row> gaps = new ArrayList<>();
        for (UnitHighlight highlight : highlights) {
            BoardReport.Row row = new BoardReport.Row(sentence(highlight, labels), List.of());
            (STRENGTHS.contains(highlight.type()) ? strengths : gaps).add(row);
        }
        String empty = labels.text(EMPTY);
        return page("highlights", labels, List.of(), List.of(
            new BoardReport.Table(labels.text("board.highlights.strengths"), List.of(), strengths, empty),
            new BoardReport.Table(labels.text("board.highlights.gaps"), List.of(), gaps, empty)), List.of());
    }

    static BoardReport.Page compliance(ComplianceSummary summary, ReportLabels labels, ZoneId zone) {
        List<String> notes = new ArrayList<>();
        notes.add(labels.text("board.compliance.status." + summary.status().name()));
        summary.attention().forEach(reason -> notes.add(labels.text("board.compliance.reason." + reason.name())));
        notes.add(labels.text("board.compliance.readAt", TIME.format(summary.generatedAt().atZone(zone))));
        List<BoardReport.Row> rows = List.of(
            item(labels.text("board.compliance.notice", summary.privacyNotice().version()),
                labels.text("board.compliance.ofTotal", String.valueOf(summary.privacyNotice().acknowledged()),
                    String.valueOf(summary.privacyNotice().accounts()))),
            item(labels.text("board.compliance.consent"),
                labels.text("board.compliance.ofTotal", String.valueOf(summary.analyticsConsent().granted()),
                    String.valueOf(summary.analyticsConsent().accounts()))),
            item(labels.text("board.compliance.erasurePending"), String.valueOf(summary.erasure().pending())),
            item(labels.text("board.compliance.erasurePostponed"), String.valueOf(summary.erasure().postponed())),
            item(labels.text("board.compliance.erasureOverdue"), String.valueOf(summary.erasure().overdue())),
            item(labels.text("board.compliance.legalHolds"), String.valueOf(summary.legalHolds().active())),
            item(labels.text("board.compliance.retention"), retention(summary.lastRetentionRun(), labels, zone)),
            item(labels.text("board.compliance.reviewsOpen"), String.valueOf(summary.reviews().open())),
            item(labels.text("board.compliance.reviewsOverdue"), String.valueOf(summary.reviews().overdue())));
        BoardReport.Table figures = new BoardReport.Table(null, List.of(labels.text("board.column.item"),
            labels.text("board.column.value")), rows, labels.text(EMPTY));
        BoardReport.Table byLevel = new BoardReport.Table(labels.text("board.compliance.byLevel"),
            List.of(labels.text("board.column.level"), labels.text("board.compliance.reviewsOpen"),
                labels.text("board.compliance.reviewsOverdue")),
            summary.reviews().byLevel().stream().map(level -> new BoardReport.Row(
                labels.text("board.review." + level.level().name()),
                List.of(String.valueOf(level.open()), String.valueOf(level.overdue())))).toList(),
            labels.text(EMPTY));
        return page("compliance", labels, notes, List.of(figures, byLevel), List.of());
    }

    private static BoardReport.Table comparisonTable(String title, String item, List<BoardReport.Row> rows,
                                                     String empty, ReportLabels labels) {
        return new BoardReport.Table(labels.text(title), List.of(labels.text(item), labels.text(CURRENT),
            labels.text(PREVIOUS), labels.text(CHANGE)), rows, empty);
    }

    private static List<BoardReport.Row> categoryRows(List<CategoryTrend> trends, ReportLabels labels) {
        return trends.stream().map(trend -> new BoardReport.Row(
            labels.language().name(trend.category().name(), trend.category().nameUk()),
            counts(trend.current(), trend.previous(), trend.delta(), trend.deltaPercent()))).toList();
    }

    private static List<BoardReport.Row> sourceRows(List<SourceTrend> trends) {
        return trends.stream().map(trend -> new BoardReport.Row(trend.name(),
            counts(trend.current(), trend.previous(), trend.delta(), trend.deltaPercent()))).toList();
    }

    private static String sentence(UnitHighlight highlight, ReportLabels labels) {
        String type = highlight.type() == UnitHighlight.Type.NO_AWARDS && highlight.previous() == 0
            ? "NO_AWARDS_EVER" : highlight.type().name();
        String key = UNIVERSITY_WORDING.contains(highlight.type()) ? "board.highlights." : "highlights.";
        return labels.text(key + type, labels.name(highlight.unit()), String.valueOf(highlight.current()),
            String.valueOf(highlight.previous()));
    }

    private static String retention(ComplianceFigures.RetentionRun run, ReportLabels labels, ZoneId zone) {
        if (run == null) {
            return labels.text("board.compliance.noRun");
        }
        return labels.text("board.compliance.run", labels.text("board.compliance.runStatus." + run.status().name()),
            TIME.format(run.startedAt().atZone(zone)));
    }

    private static BoardReport.Row item(String label, String value) {
        return new BoardReport.Row(label, List.of(value));
    }
}
