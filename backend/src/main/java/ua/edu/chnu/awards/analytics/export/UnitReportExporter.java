package ua.edu.chnu.awards.analytics.export;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import ua.edu.chnu.awards.analytics.dto.UnitAnalytics;
import ua.edu.chnu.awards.analytics.service.UnitAnalyticsService;
import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;

/**
 * Produces the analytics report of one unit as a file and records the export.
 */
@Service
public class UnitReportExporter {

    private static final String UNSAFE_CODE = "[^A-Za-z0-9_-]+";

    private final UnitAnalyticsService unitAnalytics;
    private final UnitReportAssembler assembler;
    private final Map<ReportFormat, ReportWriter> writers = new EnumMap<>(ReportFormat.class);
    private final AuditService audit;
    private final UserAccounts users;
    private final Clock clock;

    /**
     * Creates the exporter.
     *
     * @param unitAnalytics the analytics of the units in the caller's scope
     * @param assembler     lays the analytics out as a report
     * @param writers       one writer per format
     * @param audit         the audit trail
     * @param users         the accounts, for the name of the producer
     * @param clock         the Kyiv clock
     */
    public UnitReportExporter(UnitAnalyticsService unitAnalytics, UnitReportAssembler assembler,
                              List<ReportWriter> writers, AuditService audit, UserAccounts users, Clock clock) {
        this.unitAnalytics = unitAnalytics;
        this.assembler = assembler;
        writers.forEach(writer -> this.writers.put(writer.format(), writer));
        this.audit = audit;
        this.users = users;
        this.clock = clock;
    }

    /**
     * Produces the report with the figures of {@link UnitAnalyticsService#of} and writes one
     * {@link AuditAction#ANALYTICS_EXPORTED} row once the file exists.
     *
     * @param unitId   the unit
     * @param from     first calendar year, or null
     * @param to       last calendar year, or null
     * @param format   the file format
     * @param language the language of the labels
     * @param userId   the caller
     * @return the file
     */
    public ReportFile export(long unitId, Integer from, Integer to, ReportFormat format, ReportLanguage language,
                             long userId) {
        UnitAnalytics analytics = unitAnalytics.of(unitId, from, to);
        ReportWriter writer = writers.get(format);
        if (writer == null) {
            throw new IllegalStateException("No writer for " + format);
        }
        String producer = users.findById(userId).map(UnitReportExporter::fullName).orElse("");
        UnitReport report = assembler.assemble(analytics, language, ZonedDateTime.now(clock), producer);
        byte[] content = writer.write(report);
        YearRange period = analytics.period();
        audit.record(AuditAction.ANALYTICS_EXPORTED, AuditEntityConstants.ANALYTICS, userId, unitId,
            Map.of("format", format.extension(), "from", period.from(), "to", period.to(),
                "language", language.code(), "peopleNamed", report.peopleNamed()));
        return new ReportFile(fileName(analytics.unit().code(), unitId, period, format), format, content);
    }

    static String fileName(String code, long unitId, YearRange period, ReportFormat format) {
        String unit = code == null || code.isBlank() ? String.valueOf(unitId) : code.replaceAll(UNSAFE_CODE, "-");
        return "analytics-" + unit + "-" + period.from() + "-" + period.to() + "." + format.extension();
    }

    private static String fullName(User user) {
        return (user.getFirstName() + " " + user.getLastName()).strip();
    }
}
