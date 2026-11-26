package ua.edu.chnu.awards.analytics.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.messages;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.award.dto.CategoryRef;
import ua.edu.chnu.awards.award.dto.TimelineEntry;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.award.service.AwardStatistics;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;

class CvExportServiceTest {

    private static final long USER_ID = 41L;
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 11, 17, 12, 0, 0, 0, ZoneId.of("Europe/Kyiv"));
    private static final CategoryRef NATIONAL = new CategoryRef(12L, "State prize", "Державна премія",
        RecognitionLevel.NATIONAL);
    private static final CategoryRef FACULTY = new CategoryRef(31L, "Faculty award", null, RecognitionLevel.FACULTY);

    private final AwardStatistics statistics = mock(AwardStatistics.class);
    private final UserAccounts users = mock(UserAccounts.class);
    private final CvPdfWriter writer = mock(CvPdfWriter.class);
    private final AuditService audit = mock(AuditService.class);
    private final User owner = mock(User.class);
    private final CvExportService service = new CvExportService(statistics, users, messages(), writer, audit,
        Clock.fixed(NOW.toInstant(), NOW.getZone()));

    @BeforeEach
    void setUp() {
        when(owner.getFirstName()).thenReturn("Олена");
        when(owner.getLastName()).thenReturn("Коваль");
        when(users.findById(USER_ID)).thenReturn(Optional.of(owner));
    }

    @Test
    void ac4_3_theTextListsTheAwardsByYearNewestFirstInUkrainian() {
        when(statistics.approved(USER_ID)).thenReturn(awards());

        CvFile file = service.export(USER_ID, CvFormat.TEXT, ReportLanguage.UK);

        assertThat(file.name()).isEqualTo("awards-cv-2026-11-17.txt");
        assertThat(file.mediaType()).isEqualTo("text/plain;charset=UTF-8");
        assertThat(new String(file.content(), StandardCharsets.UTF_8)).isEqualTo(String.join("\n",
            "Нагороди: Олена Коваль",
            "Сформовано 17.11.2026",
            "",
            "2025",
            "- Державна премія — Національний — Кабінет Міністрів — 03.06.2025",
            "- Faculty award — Факультетський — 10.01.2025",
            "",
            "2019",
            "- Подяка — Національний — МОН — 01.09.2019",
            ""));
        verifyNoInteractions(writer);
    }

    @Test
    void ac4_3_englishLabelsKeepTheEnglishTitles() {
        when(statistics.approved(USER_ID)).thenReturn(awards());

        String text = new String(service.export(USER_ID, CvFormat.TEXT, ReportLanguage.EN).content(),
            StandardCharsets.UTF_8);

        assertThat(text).startsWith("Awards: Олена Коваль\nGenerated on 17 Nov 2026\n")
            .contains("- State prize — National — Кабінет Міністрів — 3 Jun 2025", "- Letter — National — МОН");
    }

    @Test
    void ac4_3_ac4_6_thePdfIsRenderedAndOneAuditRowCarriesFormatLanguageAndCount() {
        when(statistics.approved(USER_ID)).thenReturn(awards());
        when(writer.write(any())).thenReturn(new byte[] {1, 2});

        CvFile file = service.export(USER_ID, CvFormat.PDF, ReportLanguage.EN);

        assertThat(file.content()).containsExactly(1, 2);
        assertThat(file.name()).isEqualTo("awards-cv-2026-11-17.pdf");
        verify(audit).record(AuditAction.ANALYTICS_EXPORTED, AuditEntityConstants.ANALYTICS, USER_ID, USER_ID,
            Map.of("format", "cv-pdf", "language", "en", "count", 3L));
    }

    @Test
    void ac4_5_withoutAwardsTheTextIsTheHeadingOnly() {
        when(statistics.approved(USER_ID)).thenReturn(List.of());

        CvFile file = service.export(USER_ID, CvFormat.TEXT, ReportLanguage.UK);

        assertThat(new String(file.content(), StandardCharsets.UTF_8))
            .isEqualTo("Нагороди: Олена Коваль\nСформовано 17.11.2026\n");
        verify(audit).record(AuditAction.ANALYTICS_EXPORTED, AuditEntityConstants.ANALYTICS, USER_ID, USER_ID,
            Map.of("format", "cv-text", "language", "uk", "count", 0L));
    }

    @Test
    void ac4_3_anUnknownFormatNamesTheParameter() {
        assertThatThrownBy(() -> CvFormat.of("docx")).isInstanceOfSatisfying(ApiProblemException.class, problem -> {
            assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(problem.getProperties()).containsEntry("parameter", "format");
        });
        assertThat(CvFormat.of("text")).isEqualTo(CvFormat.TEXT);
    }

    private static List<TimelineEntry> awards() {
        return List.of(
            new TimelineEntry(3L, "State prize", "Державна премія", LocalDate.of(2025, 6, 3), NATIONAL,
                "Кабінет Міністрів", true, NATIONAL.level()),
            new TimelineEntry(2L, "Faculty award", null, LocalDate.of(2025, 1, 10), FACULTY, null, false,
                FACULTY.level()),
            new TimelineEntry(1L, "Letter", "Подяка", LocalDate.of(2019, 9, 1), NATIONAL, "МОН", false,
                NATIONAL.level()));
    }
}
