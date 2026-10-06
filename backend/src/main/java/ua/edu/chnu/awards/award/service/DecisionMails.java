package ua.edu.chnu.awards.award.service;

import static ua.edu.chnu.awards.common.mail.MailTextHelper.SEPARATOR;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloEn;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloUk;

import java.util.Map;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.event.AwardDecided;
import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.config.AuthProperties;

import lombok.RequiredArgsConstructor;

/**
 * Tells the owner of an award about a reviewer decision once it commits: approved, passed on to the next level,
 * returned for changes or rejected, with the reviewer's comment and a link to the award.
 */
@Component
@RequiredArgsConstructor
public class DecisionMails {

    private static final Map<ApprovalLevel, String> TO_LEVEL_UK = Map.of(
        ApprovalLevel.FACULTY_SECRETARY, "секретарю факультету",
        ApprovalLevel.DEAN, "декану",
        ApprovalLevel.RECTOR_SECRETARY, "секретарю ректора",
        ApprovalLevel.RECTOR, "ректору");
    private static final Map<ApprovalLevel, String> TO_LEVEL_EN = Map.of(
        ApprovalLevel.FACULTY_SECRETARY, "the faculty secretary",
        ApprovalLevel.DEAN, "the dean",
        ApprovalLevel.RECTOR_SECRETARY, "the rector's secretary",
        ApprovalLevel.RECTOR, "the rector");

    private final MailDelivery delivery;
    private final AuthProperties properties;

    /**
     * Sends the owner one message about the decision.
     *
     * @param event the committed decision
     */
    @Async
    @TransactionalEventListener
    public void onDecided(AwardDecided event) {
        if (event.email() == null) {
            return;
        }
        delivery.send(event.email(), subject(event), body(event, properties.frontendUrl() + "/awards/"
            + event.awardId()));
    }

    static String subject(AwardDecided event) {
        return switch (event.outcome()) {
            case APPROVED -> "Нагороду затверджено / Award approved";
            case RETURNED -> "Нагороду повернуто на доопрацювання / Award returned for changes";
            case REJECTED -> "Нагороду відхилено / Award rejected";
            default -> "Нагороду передано далі / Award passed on";
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
        return switch (event.outcome()) {
            case APPROVED -> event.reviewer() + " затвердив(ла) нагороду.";
            case RETURNED -> event.reviewer() + " повернув(ла) нагороду на доопрацювання. Виправте її та подайте "
                + "ще раз.";
            case REJECTED -> event.reviewer() + " відхилив(ла) нагороду.";
            default -> event.reviewer() + " передав(ла) нагороду " + TO_LEVEL_UK.get(event.level()) + ".";
        };
    }

    private static String outcomeEn(AwardDecided event) {
        return switch (event.outcome()) {
            case APPROVED -> event.reviewer() + " approved the award.";
            case RETURNED -> event.reviewer() + " returned the award for changes. Correct it and submit it again.";
            case REJECTED -> event.reviewer() + " rejected the award.";
            default -> event.reviewer() + " passed the award on to " + TO_LEVEL_EN.get(event.level()) + ".";
        };
    }
}
