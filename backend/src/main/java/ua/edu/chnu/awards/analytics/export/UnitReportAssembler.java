package ua.edu.chnu.awards.analytics.export;

import static ua.edu.chnu.awards.analytics.export.ReportLabels.APPROVED;
import static ua.edu.chnu.awards.analytics.export.ReportLabels.AWARDED_PEOPLE;
import static ua.edu.chnu.awards.analytics.export.ReportLabels.MEMBERS;
import static ua.edu.chnu.awards.analytics.export.ReportLabels.PERSONAL;
import static ua.edu.chnu.awards.analytics.export.ReportLabels.PER_HUNDRED;
import static ua.edu.chnu.awards.analytics.export.ReportLabels.UNIT_AWARDS;
import static ua.edu.chnu.awards.analytics.export.ReportLabels.VERIFIED;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.analytics.dto.SourceSummary;
import ua.edu.chnu.awards.analytics.dto.UnitAnalytics;
import ua.edu.chnu.awards.analytics.dto.UnitTotals;
import ua.edu.chnu.awards.analytics.dto.UnitYearCount;
import ua.edu.chnu.awards.award.dto.YearRange;

import lombok.RequiredArgsConstructor;

/**
 * Lays the unit analytics out as the labelled tables of a report.
 */
@Component
@RequiredArgsConstructor
public class UnitReportAssembler {

    private static final DateTimeFormatter PRODUCED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MessageSource messages;

    /**
     * Builds the report.
     *
     * @param analytics  the analytics of the unit
     * @param language   the language of the labels
     * @param producedAt the moment of production in the Kyiv zone
     * @param producedBy the name of the person producing it
     * @return the report
     */
    public UnitReport assemble(UnitAnalytics analytics, ReportLanguage language, ZonedDateTime producedAt,
                               String producedBy) {
        ReportLabels labels = new ReportLabels(messages, language);
        String unit = language.name(analytics.unit().name(), analytics.unit().nameUk());
        List<UnitReport.Field> header = new ArrayList<>(List.of(
            labels.field("unit", unit),
            labels.field("period", years(analytics.period())),
            labels.field("previousPeriod", years(analytics.previousPeriod())),
            labels.field("producedAt", PRODUCED_AT.format(producedAt)),
            labels.field("producedBy", producedBy)));
        if (analytics.partialYear()) {
            header.add(labels.field("partialYear",
                labels.text("partialYear", String.valueOf(analytics.period().to()))));
        }
        List<UnitReport.Section> sections = List.of(
            labels.section("totals", totals(analytics.totals(), labels)),
            labels.section("byYear", byYear(analytics.byYear(), labels)),
            labels.section("departments",
                UnitReportRowsHelper.departments(analytics.units(), analytics.period().from(), labels)),
            labels.section("byCategory", analytics.byCategory().stream()
                .map(row -> UnitReport.Row.counts(String.valueOf(row.category().id()),
                    language.name(row.category().name(), row.category().nameUk()), row.count(), row.previous()))
                .toList()),
            labels.section("byLevel", analytics.byLevel().stream()
                .map(row -> UnitReport.Row.counts(row.level().name(), labels.level(row.level()), row.count(),
                    row.previous()))
                .toList()),
            labels.section("bySource", bySource(analytics.bySource(), labels)),
            labels.section(UnitReport.Section.TOP_PERFORMERS,
                UnitReportRowsHelper.topPerformers(analytics.topPerformers(), labels)),
            labels.section("highlights", UnitReportRowsHelper.highlights(analytics.highlights(), labels)));
        List<String> columns = List.of(labels.text("column.key"), labels.text("column.label"),
            labels.text("column.year"), labels.text("column.current"), labels.text("column.previous"));
        return new UnitReport(language, labels.text("title", unit), header, labels.text("note"),
            labels.text("empty"), columns, sections);
    }

    private static List<UnitReport.Row> totals(UnitTotals totals, ReportLabels labels) {
        return List.of(
            UnitReport.Row.counts(APPROVED, labels.figure(APPROVED), totals.approved().current(),
                totals.approved().previous()),
            UnitReport.Row.counts(AWARDED_PEOPLE, labels.figure(AWARDED_PEOPLE), totals.awardedPeople().current(),
                totals.awardedPeople().previous()),
            UnitReport.Row.count(PERSONAL, labels.figure(PERSONAL), totals.personal()),
            UnitReport.Row.count(UNIT_AWARDS, labels.figure(UNIT_AWARDS), totals.unitAwards()),
            UnitReport.Row.count(VERIFIED, labels.figure(VERIFIED), totals.verified()),
            UnitReport.Row.count(MEMBERS, labels.figure(MEMBERS), totals.members()),
            UnitReport.Row.figure(PER_HUNDRED, labels.figure(PER_HUNDRED), totals.perHundredMembers()));
    }

    private static List<UnitReport.Row> byYear(List<UnitYearCount> years, ReportLabels labels) {
        List<UnitReport.Row> rows = new ArrayList<>();
        for (UnitYearCount year : years) {
            rows.add(UnitReport.Row.inYear("total", labels.figure(APPROVED), year.year(), year.total()));
            rows.add(UnitReport.Row.inYear(PERSONAL, labels.figure(PERSONAL), year.year(), year.personal()));
            rows.add(UnitReport.Row.inYear(UNIT_AWARDS, labels.figure(UNIT_AWARDS), year.year(), year.unitAwards()));
        }
        return rows;
    }

    private static List<UnitReport.Row> bySource(SourceSummary summary, ReportLabels labels) {
        List<UnitReport.Row> rows = new ArrayList<>();
        for (int i = 0; i < summary.sources().size(); i++) {
            SourceSummary.Source source = summary.sources().get(i);
            rows.add(UnitReport.Row.count(String.valueOf(i + 1), source.name(), source.count()));
        }
        if (summary.otherCount() > 0) {
            rows.add(UnitReport.Row.count("others", labels.text("sources.others"), summary.otherCount()));
        }
        return rows;
    }

    private static String years(YearRange range) {
        return range.from() == range.to() ? String.valueOf(range.from()) : range.from() + "–" + range.to();
    }
}
