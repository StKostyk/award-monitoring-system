package ua.edu.chnu.awards.analytics.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.FACULTY_ID;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.PRODUCED_AT;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.faculty;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.messages;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.analytics.service.UnitAnalyticsService;
import ua.edu.chnu.awards.analytics.service.UnitNotFoundException;
import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;

class UnitReportExporterTest {

    private static final long DEAN = 7L;

    private final UnitAnalyticsService analytics = mock(UnitAnalyticsService.class);
    private final AuditService audit = mock(AuditService.class);
    private final UserAccounts users = mock(UserAccounts.class);
    private final User dean = mock(User.class);
    private UnitReportExporter exporter;

    @BeforeEach
    void setUp() {
        exporter = new UnitReportExporter(analytics, new UnitReportAssembler(messages()),
            List.of(new CsvReportWriter(), new XlsxReportWriter()), audit, users,
            Clock.fixed(PRODUCED_AT.toInstant(), PRODUCED_AT.getZone()));
        when(dean.getFirstName()).thenReturn("Olena");
        when(dean.getLastName()).thenReturn("Koval");
        when(users.findById(DEAN)).thenReturn(Optional.of(dean));
    }

    @Test
    void ac3_1_theFileIsNamedAfterUnitCodeAndPeriod() {
        when(analytics.of(FACULTY_ID, 2022, null)).thenReturn(faculty());

        ReportFile file = exporter.export(FACULTY_ID, 2022, null, ReportFormat.CSV, ReportLanguage.EN, DEAN);

        assertThat(file.name()).isEqualTo("analytics-FMI-2022-2026.csv");
        assertThat(file.format()).isEqualTo(ReportFormat.CSV);
        assertThat(new String(file.content(), StandardCharsets.UTF_8))
            .contains("report,producedBy,Olena Koval,,,", "report,producedAt,2026-10-23 14:05,,,");
    }

    @Test
    void ac3_5_theExportIsAuditedWithFormatPeriodLanguageAndPeopleNamed() {
        when(analytics.of(FACULTY_ID, null, null)).thenReturn(faculty());

        exporter.export(FACULTY_ID, null, null, ReportFormat.XLSX, ReportLanguage.UK, DEAN);

        verify(audit).record(AuditAction.ANALYTICS_EXPORTED, AuditEntityConstants.ANALYTICS, DEAN, FACULTY_ID,
            Map.of("format", "xlsx", "from", 2022, "to", 2026, "language", "uk", "peopleNamed", 5L));
    }

    @Test
    void ac3_5_aRefusedRequestWritesNoAuditRow() {
        when(analytics.of(anyLong(), any(), any())).thenThrow(new UnitNotFoundException(FACULTY_ID));

        assertThatThrownBy(() -> exporter.export(FACULTY_ID, null, null, ReportFormat.CSV, ReportLanguage.EN, DEAN))
            .isInstanceOf(UnitNotFoundException.class);
        verifyNoInteractions(audit);
    }

    @Test
    void ac3_1_unknownFormatAndLanguageNameTheParameter() {
        assertThat(ReportFormat.of("PDF")).isEqualTo(ReportFormat.PDF);
        assertThat(ReportLanguage.of("en")).isEqualTo(ReportLanguage.EN);
        assertThatThrownBy(() -> ReportFormat.of("docx")).isInstanceOfSatisfying(ApiProblemException.class,
            problem -> {
                assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(problem.getProperties()).containsEntry("parameter", "format");
            });
        assertThatThrownBy(() -> ReportFormat.of(null)).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(() -> ReportLanguage.of("de")).isInstanceOfSatisfying(ApiProblemException.class,
            problem -> assertThat(problem.getProperties()).containsEntry("parameter", "lang"));
    }

    @Test
    void ac3_1_aUnitWithoutCodeIsNamedByItsId() {
        YearRange period = new YearRange(2022, 2026);

        assertThat(UnitReportExporter.fileName(null, FACULTY_ID, period, ReportFormat.PDF))
            .isEqualTo("analytics-9-2022-2026.pdf");
        assertThat(UnitReportExporter.fileName("F M/I", FACULTY_ID, period, ReportFormat.PDF))
            .isEqualTo("analytics-F-M-I-2022-2026.pdf");
    }

    @Test
    void ac3_7_aFormatWithoutAWriterIsRefused() {
        when(analytics.of(FACULTY_ID, null, null)).thenReturn(faculty());

        assertThatThrownBy(() -> exporter.export(FACULTY_ID, null, null, ReportFormat.PDF, ReportLanguage.EN, DEAN))
            .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(audit);
    }
}
