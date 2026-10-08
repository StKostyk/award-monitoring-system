package ua.edu.chnu.awards.award.service;

import static ua.edu.chnu.awards.common.mail.MailTextHelper.SEPARATOR;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloEn;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloUk;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.event.OverdueNoticed;
import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.config.AuthProperties;

import lombok.RequiredArgsConstructor;

/**
 * Sends a reviewer of the next level one digest of the overdue requests the job marked for him, once the marks
 * commit; a message the mail server refuses is logged and not sent again.
 */
@Component
@RequiredArgsConstructor
public class OverdueMails {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        .withZone(ZoneId.of("Europe/Kyiv"));
    private static final Map<ApprovalLevel, String> LEVEL_UK = Map.of(
        ApprovalLevel.FACULTY_SECRETARY, "секретар факультету",
        ApprovalLevel.DEAN, "декан",
        ApprovalLevel.RECTOR_SECRETARY, "секретар ректора",
        ApprovalLevel.RECTOR, "ректор");
    private static final Map<ApprovalLevel, String> LEVEL_EN = Map.of(
        ApprovalLevel.FACULTY_SECRETARY, "faculty secretary",
        ApprovalLevel.DEAN, "dean",
        ApprovalLevel.RECTOR_SECRETARY, "rector's secretary",
        ApprovalLevel.RECTOR, "rector");

    private final MailDelivery delivery;
    private final AuthProperties properties;
    private final ReviewMetrics metrics;

    /**
     * Sends the digest and counts it when the mail server accepts it.
     *
     * @param event the committed digest
     */
    @Async
    @TransactionalEventListener
    public void onNoticed(OverdueNoticed event) {
        if (event.email() != null && delivery.send(event.email(), subject(event), body(event,
            properties.frontendUrl() + "/awards/"))) {
            metrics.noticeSent();
        }
    }

    static String subject(OverdueNoticed event) {
        int count = event.requests().size();
        return "Прострочені заявки: " + count + " / Overdue requests: " + count;
    }

    static String body(OverdueNoticed event, String linkPrefix) {
        StringBuilder uk = new StringBuilder(helloUk(event.name()))
            .append("Термін розгляду цих заявок минув на нижчому рівні:\n\n");
        StringBuilder en = new StringBuilder(helloEn(event.name()))
            .append("The review period of these requests has passed at the level below:\n\n");
        for (OverdueNoticed.Item item : event.requests()) {
            String deadline = DATE.format(item.deadline());
            String link = linkPrefix + item.awardId();
            uk.append("- «").append(item.titleUk()).append("», ").append(item.owner())
                .append("; рівень: ").append(LEVEL_UK.get(item.level()))
                .append("; термін: ").append(deadline)
                .append("; розглядає: ").append(item.reviewer() == null ? "не взято" : item.reviewer())
                .append("\n  ").append(link).append('\n');
            en.append("- \"").append(item.title()).append("\", ").append(item.owner())
                .append("; level: ").append(LEVEL_EN.get(item.level()))
                .append("; deadline: ").append(deadline)
                .append("; reviewer: ").append(item.reviewer() == null ? "not taken" : item.reviewer())
                .append("\n  ").append(link).append('\n');
        }
        return uk.append('\n').append(SEPARATOR).append(en).toString();
    }
}
