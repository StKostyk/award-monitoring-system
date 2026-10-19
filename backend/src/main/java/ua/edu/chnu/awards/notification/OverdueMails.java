package ua.edu.chnu.awards.notification;

import static ua.edu.chnu.awards.common.mail.MailTextHelper.SEPARATOR;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.dateTime;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloEn;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloUk;

import java.time.Clock;
import java.time.ZoneId;
import java.util.function.LongFunction;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import ua.edu.chnu.awards.award.event.OverdueNoticed;
import ua.edu.chnu.awards.award.service.AwardLinks;
import ua.edu.chnu.awards.award.service.ReviewMetrics;
import ua.edu.chnu.awards.notification.service.DeliveryLog;
import ua.edu.chnu.awards.notification.service.NotificationDrafts;

import lombok.RequiredArgsConstructor;

/**
 * Sends a reviewer of the next level one digest of the overdue requests the job marked for him, once the marks
 * commit; a message the mail server refuses is logged and not sent again.
 */
@Component
@RequiredArgsConstructor
public class OverdueMails {

    private final DeliveryLog deliveries;
    private final NotificationDrafts drafts;
    private final AwardLinks links;
    private final ReviewMetrics metrics;
    private final Clock clock;

    /**
     * Sends the digest and counts it once the mail server accepted it.
     *
     * @param event the committed digest
     */
    @Async
    @TransactionalEventListener
    public void onNoticed(OverdueNoticed event) {
        String body = body(event, links::awardLink, clock.getZone());
        if (deliveries.send(drafts.of(event), event.email(), subject(event), body)) {
            metrics.noticeSent();
        }
    }

    static String subject(OverdueNoticed event) {
        int count = event.requests().size();
        return "Прострочені заявки: " + count + " / Overdue requests: " + count;
    }

    static String body(OverdueNoticed event, LongFunction<String> awardLink, ZoneId zone) {
        StringBuilder uk = new StringBuilder(helloUk(event.name()))
            .append("Термін розгляду цих заявок минув на нижчому рівні:\n\n");
        StringBuilder en = new StringBuilder(helloEn(event.name()))
            .append("The review period of these requests has passed at the level below:\n\n");
        for (OverdueNoticed.Item item : event.requests()) {
            String deadline = dateTime(item.deadline(), zone);
            String link = awardLink.apply(item.awardId());
            uk.append("- «").append(item.titleUk()).append("», ").append(item.owner())
                .append("; рівень: ").append(LevelNamesHelper.uk(item.level()))
                .append("; термін: ").append(deadline)
                .append("; розглядає: ").append(item.reviewer() == null ? "не взято" : item.reviewer())
                .append("\n  ").append(link).append('\n');
            en.append("- \"").append(item.title()).append("\", ").append(item.owner())
                .append("; level: ").append(LevelNamesHelper.en(item.level()))
                .append("; deadline: ").append(deadline)
                .append("; reviewer: ").append(item.reviewer() == null ? "not taken" : item.reviewer())
                .append("\n  ").append(link).append('\n');
        }
        return uk.append('\n').append(SEPARATOR).append(en).toString();
    }
}
