package ua.edu.chnu.awards.analytics.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static ua.edu.chnu.awards.analytics.export.ComplianceFixture.compliance;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.messages;
import static ua.edu.chnu.awards.analytics.export.UniversityReportFixture.PRODUCED_AT;
import static ua.edu.chnu.awards.analytics.export.UniversityReportFixture.UNIVERSITY_ID;
import static ua.edu.chnu.awards.analytics.export.UniversityReportFixture.university;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.analytics.service.UniversityAnalyticsService;
import ua.edu.chnu.awards.analytics.service.UniversityComplianceService;
import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;

class BoardReportExporterTest {

    private static final long RECTOR = 3L;
    private static final byte[] PDF = {'%', 'P', 'D', 'F'};

    private final UniversityAnalyticsService analytics = mock(UniversityAnalyticsService.class);
    private final UniversityComplianceService compliance = mock(UniversityComplianceService.class);
    private final BoardPdfWriter writer = mock(BoardPdfWriter.class);
    private final AuditService audit = mock(AuditService.class);
    private final UserAccounts users = mock(UserAccounts.class);
    private final User rector = mock(User.class);
    private BoardReportExporter exporter;

    @BeforeEach
    void setUp() {
        exporter = new BoardReportExporter(analytics, compliance, new BoardReportAssembler(messages()), writer, audit,
            users, Clock.fixed(PRODUCED_AT.toInstant(), PRODUCED_AT.getZone()));
        when(rector.getFirstName()).thenReturn("Ivan");
        when(rector.getLastName()).thenReturn("Rector");
        when(users.findById(RECTOR)).thenReturn(Optional.of(rector));
        when(compliance.summary()).thenReturn(compliance());
        when(writer.write(any())).thenReturn(PDF);
    }

    @Test
    void ac3_1_theFileIsAPdfNamedAfterTheUniversityCodeAndPeriod() {
        when(analytics.of(2022, null)).thenReturn(university());

        ReportFile file = exporter.export(2022, null, ReportLanguage.EN, RECTOR);

        assertThat(file.name()).isEqualTo("board-report-CHNU-2022-2026.pdf");
        assertThat(file.format()).isEqualTo(ReportFormat.PDF);
        assertThat(file.content()).isEqualTo(PDF);
    }

    @Test
    void ac3_2_theReportCarriesTheProducerAndTheFiguresOfBothReads() {
        when(analytics.of(null, null)).thenReturn(university());

        exporter.export(null, null, ReportLanguage.EN, RECTOR);

        verify(writer).write(new BoardReportAssembler(messages()).assemble(university(), compliance(),
            ReportLanguage.EN, PRODUCED_AT, "Ivan Rector"));
    }

    @Test
    void ac3_4_theExportIsAuditedAsABoardPdfNamingNobody() {
        when(analytics.of(null, null)).thenReturn(university());

        exporter.export(null, null, ReportLanguage.UK, RECTOR);

        verify(audit).record(AuditAction.ANALYTICS_EXPORTED, AuditEntityConstants.ANALYTICS, RECTOR, UNIVERSITY_ID,
            Map.of("format", "board-pdf", "from", 2022, "to", 2026, "language", "uk", "peopleNamed", 0L));
    }

    @Test
    void ac3_4_aRefusedRequestWritesNoAuditRow() {
        when(analytics.of(2026, 2021)).thenThrow(new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-period",
            "The period is invalid"));

        assertThatThrownBy(() -> exporter.export(2026, 2021, ReportLanguage.EN, RECTOR))
            .isInstanceOf(ApiProblemException.class);
        verifyNoInteractions(audit, writer);
    }

    @Test
    void ac3_1_aUniversityWithoutCodeIsNamedByItsId() {
        YearRange period = new YearRange(2022, 2026);

        assertThat(BoardReportExporter.fileName(" ", UNIVERSITY_ID, period)).isEqualTo("board-report-1-2022-2026.pdf");
        assertThat(BoardReportExporter.fileName("Ch NU/1", UNIVERSITY_ID, period))
            .isEqualTo("board-report-Ch-NU-1-2022-2026.pdf");
    }
}
