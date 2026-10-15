package ua.edu.chnu.awards.notification;

import static ua.edu.chnu.awards.common.mail.MailTextHelper.SEPARATOR;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloEn;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloUk;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import ua.edu.chnu.awards.award.event.AwardDecided;
import ua.edu.chnu.awards.award.service.AwardLinks;
import ua.edu.chnu.awards.notification.service.DeliveryLog;
import ua.edu.chnu.awards.notification.service.NotificationDrafts;

import lombok.RequiredArgsConstructor;

/**
 * Tells the owner of an award about a reviewer decision once it commits: approved, passed on to the next level,
 * returned for changes or rejected, with the reviewer's comment and a link to the award.
 */
@Component
@RequiredArgsConstructor
public class DecisionMails {

    private static final String NOT_A_DECISION = "Not a decision: ";

    private final DeliveryLog deliveries;
    private final NotificationDrafts drafts;
    private final AwardLinks links;

    /**
     * Sends the owner one message about the decision, unless the owner switched these mails off.
     *
     * @param event the committed decision
     */
    @Async
    @TransactionalEventListener
    public void onDecided(AwardDecided event) {
        deliveries.send(drafts.of(event), event.email(), subject(event),
            body(event, links.awardLink(event.awardId())));
    }

    static String subject(AwardDecided event) {
        return switch (NotificationDrafts.typeOf(event)) {
            case AWARD_APPROVED -> "Нагороду затверджено / Award approved";
            case AWARD_RETURNED -> "Нагороду повернуто на доопрацювання / Award returned for changes";
            case AWARD_REJECTED -> "Нагороду відхилено / Award rejected";
            case AWARD_ESCALATED -> "Нагороду передано далі / Award passed on";
            default -> throw new IllegalStateException(NOT_A_DECISION + event.outcome());
        };
    }

    static String body(AwardDecided event, String link) {
        String comment = event.comment() == null ? "" : "Коментар: " + event.comment() + "\n";
        String commentEn = event.comment() == null ? "" : "Comment: " + event.comment() + "\n";
        return helloUk(event.firstName())
            + "«" + event.titleUk() + "»: " + outcomeUk(event) + "\n"
            + comment
            + "Переглянути нагороду: " + link + "\n\n"
            + SEPARATOR
            + helloEn(event.firstName())
            + "\"" + event.title() + "\": " + outcomeEn(event) + "\n"
            + commentEn
            + "View the award: " + link + "\n";
    }

    private static String outcomeUk(AwardDecided event) {
        return switch (NotificationDrafts.typeOf(event)) {
            case AWARD_APPROVED -> event.reviewer() + " затвердив(ла) нагороду.";
            case AWARD_RETURNED -> event.reviewer() + " повернув(ла) нагороду на доопрацювання. Виправте її та подайте "
                + "ще раз.";
            case AWARD_REJECTED -> event.reviewer() + " відхилив(ла) нагороду.";
            case AWARD_ESCALATED -> event.reviewer() + " передав(ла) нагороду " + LevelNamesHelper.toUk(event.level())
                + ".";
            default -> throw new IllegalStateException(NOT_A_DECISION + event.outcome());
        };
    }

    private static String outcomeEn(AwardDecided event) {
        return switch (NotificationDrafts.typeOf(event)) {
            case AWARD_APPROVED -> event.reviewer() + " approved the award.";
            case AWARD_RETURNED -> event.reviewer() + " returned the award for changes. Correct it and submit it "
                + "again.";
            case AWARD_REJECTED -> event.reviewer() + " rejected the award.";
            case AWARD_ESCALATED -> event.reviewer() + " passed the award on to the "
                + LevelNamesHelper.en(event.level()) + ".";
            default -> throw new IllegalStateException(NOT_A_DECISION + event.outcome());
        };
    }
}
