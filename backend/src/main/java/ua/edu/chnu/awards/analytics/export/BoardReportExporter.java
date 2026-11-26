package ua.edu.chnu.awards.analytics.export;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Map;

import org.springframework.stereotype.Service;

import ua.edu.chnu.awards.analytics.dto.ComplianceSummary;
import ua.edu.chnu.awards.analytics.dto.UniversityAnalytics;
import ua.edu.chnu.awards.analytics.service.UniversityAnalyticsService;
import ua.edu.chnu.awards.analytics.service.UniversityComplianceService;
import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;

/**
 * Produces the board report of the university as a PDF and records the export.
 */
@Service
public class BoardReportExporter {

    static final String FORMAT = "board-pdf";

    private static final String UNSAFE_CODE = "[^A-Za-z0-9_-]+";

    private final UniversityAnalyticsService universityAnalytics;
    private final UniversityComplianceService compliance;
    private final BoardReportAssembler assembler;
    private final BoardPdfWriter writer;
    private final AuditService audit;
    private final UserAccounts users;
    private final Clock clock;

    /**
     * Creates the exporter.
     *
     * @param universityAnalytics the university analytics
     * @param compliance          the compliance summary
     * @param assembler           lays the figures out as pages
     * @param writer              renders the PDF
     * @param audit               the audit trail
     * @param users               the accounts, for the name of the producer
     * @param clock               the Kyiv clock
     */
    public BoardReportExporter(UniversityAnalyticsService universityAnalytics, UniversityComplianceService compliance,
                               BoardReportAssembler assembler, BoardPdfWriter writer, AuditService audit,
                               UserAccounts users, Clock clock) {
        this.universityAnalytics = universityAnalytics;
        this.compliance = compliance;
        this.assembler = assembler;
        this.writer = writer;
        this.audit = audit;
        this.users = users;
        this.clock = clock;
    }

    /**
     * Produces the report with the figures of {@link UniversityAnalyticsService#of} and
     * {@link UniversityComplianceService#summary} and writes one {@link AuditAction#ANALYTICS_EXPORTED} row once the
     * file exists.
     *
     * @param from     first calendar year, or null
     * @param to       last calendar year, or null
     * @param language the language of the labels
     * @param userId   the caller
     * @return the PDF
     */
    public ReportFile export(Integer from, Integer to, ReportLanguage language, long userId) {
        UniversityAnalytics analytics = universityAnalytics.of(from, to);
        ComplianceSummary summary = compliance.summary();
        String producer = users.findById(userId).map(BoardReportExporter::fullName).orElse("");
        BoardReport report = assembler.assemble(analytics, summary, language, ZonedDateTime.now(clock), producer);
        byte[] content = writer.write(report);
        YearRange period = analytics.period();
        long universityId = analytics.unit().id();
        audit.record(AuditAction.ANALYTICS_EXPORTED, AuditEntityConstants.ANALYTICS, userId, universityId,
            Map.of("format", FORMAT, "from", period.from(), "to", period.to(), "language", language.code(),
                "peopleNamed", 0L));
        return new ReportFile(fileName(analytics.unit().code(), universityId, period), ReportFormat.PDF, content);
    }

    static String fileName(String code, long universityId, YearRange period) {
        String unit = code == null || code.isBlank() ? String.valueOf(universityId)
            : code.replaceAll(UNSAFE_CODE, "-");
        return "board-report-" + unit + "-" + period.from() + "-" + period.to() + ".pdf";
    }

    private static String fullName(User user) {
        return (user.getFirstName() + " " + user.getLastName()).strip();
    }
}
