package ua.edu.chnu.awards.analytics.export;

import static ua.edu.chnu.awards.analytics.export.BoardFormatHelper.years;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.analytics.dto.ComplianceSummary;
import ua.edu.chnu.awards.analytics.dto.UniversityAnalytics;

import lombok.RequiredArgsConstructor;

/**
 * Lays the university analytics and the compliance summary out as the pages of the board report.
 */
@Component
@RequiredArgsConstructor
public class BoardReportAssembler {

    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MessageSource messages;

    /**
     * Builds the report.
     *
     * @param analytics  the university analytics
     * @param compliance the compliance summary
     * @param language   the language of the labels
     * @param producedAt the moment of production in the Kyiv zone
     * @param producedBy the name of the person producing it
     * @return the report
     */
    public BoardReport assemble(UniversityAnalytics analytics, ComplianceSummary compliance,
                                ReportLanguage language, ZonedDateTime producedAt, String producedBy) {
        ReportLabels labels = new ReportLabels(messages, language);
        String university = labels.name(analytics.unit());
        List<UnitReport.Field> header = new ArrayList<>(List.of(
            labels.field("university", university),
            labels.field("period", years(analytics.period())),
            labels.field("previousPeriod", years(analytics.previousPeriod())),
            labels.field("producedAt", TIME.format(producedAt)),
            labels.field("producedBy", producedBy),
            labels.field("generatedAt", TIME.format(analytics.generatedAt().atZone(producedAt.getZone())))));
        if (analytics.partialYear()) {
            header.add(labels.field("partialYear",
                labels.text("partialYear", String.valueOf(analytics.period().to()))));
        }
        List<BoardReport.Page> pages = List.of(
            BoardFiguresHelper.figures(analytics.totals(), labels),
            BoardFiguresHelper.trend(analytics, labels),
            BoardFiguresHelper.comparison(analytics.units(), labels),
            BoardPagesHelper.categories(analytics, labels),
            BoardPagesHelper.trends(analytics.trends(), labels),
            BoardPagesHelper.highlights(analytics.highlights(), labels),
            BoardPagesHelper.compliance(compliance, labels, producedAt.getZone()));
        return new BoardReport(language, labels.text("board.title", university), header, labels.text("board.note"),
            pages);
    }

    static BoardReport.Page page(String key, ReportLabels labels, List<String> notes, List<BoardReport.Table> tables,
                                 List<BoardReport.Chart> charts) {
        return new BoardReport.Page(key, labels.text("board.section." + key), notes, tables, charts);
    }
}
