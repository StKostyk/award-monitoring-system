package ua.edu.chnu.awards.analytics.export;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.award.dto.TimelineEntry;
import ua.edu.chnu.awards.award.service.AwardStatistics;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;

import lombok.RequiredArgsConstructor;

/**
 * Exports the approved personal awards of the caller, private ones included, for a CV.
 */
@Service
@RequiredArgsConstructor
public class CvExportService {

    private final AwardStatistics statistics;
    private final UserAccounts users;
    private final MessageSource messages;
    private final CvPdfWriter writer;
    private final AuditService audit;
    private final Clock clock;

    /**
     * Produces the file and writes one {@link AuditAction#ANALYTICS_EXPORTED} row with the format, the language and
     * the count, no award content.
     *
     * @param userId   the caller
     * @param format   the file format
     * @param language the language of the labels
     * @return the file
     */
    @Transactional
    public CvFile export(long userId, CvFormat format, ReportLanguage language) {
        CvReport report = assemble(statistics.approved(userId), owner(userId), language);
        byte[] content = format == CvFormat.PDF ? writer.write(report) : CvTextHelper.write(report);
        audit.record(AuditAction.ANALYTICS_EXPORTED, AuditEntityConstants.ANALYTICS, userId, userId,
            Map.of("format", "cv-" + format.code(), "language", language.code(), "count", report.count()));
        return new CvFile("awards-cv-" + LocalDate.now(clock) + "." + format.extension(), format.mediaType(),
            content);
    }

    CvReport assemble(List<TimelineEntry> awards, String owner, ReportLanguage language) {
        ReportLabels labels = new ReportLabels(messages, language);
        DateTimeFormatter dates = DateTimeFormatter.ofPattern(labels.text("cv.datePattern"), language.locale());
        Map<Integer, List<CvReport.Entry>> byYear = new LinkedHashMap<>();
        for (TimelineEntry award : awards) {
            byYear.computeIfAbsent(award.awardDate().getYear(), year -> new ArrayList<>())
                .add(new CvReport.Entry(language.name(award.title(), award.titleUk()),
                    award.category().level() == null ? "" : labels.level(award.category().level()),
                    award.awardingOrganization() == null ? "" : award.awardingOrganization(),
                    dates.format(award.awardDate())));
        }
        List<CvReport.Year> years = byYear.entrySet().stream()
            .map(year -> new CvReport.Year(year.getKey(), year.getValue()))
            .toList();
        return new CvReport(language, labels.text("cv.title", owner),
            labels.text("cv.generated", dates.format(LocalDate.now(clock))), years, labels.text("cv.empty"));
    }

    private String owner(long userId) {
        return users.findById(userId).map(CvExportService::fullName).orElse("");
    }

    private static String fullName(User user) {
        return (user.getFirstName() + " " + user.getLastName()).strip();
    }
}
