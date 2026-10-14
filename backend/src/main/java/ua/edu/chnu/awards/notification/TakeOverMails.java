package ua.edu.chnu.awards.notification;

import static ua.edu.chnu.awards.common.mail.MailTextHelper.SEPARATOR;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloEn;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloUk;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import ua.edu.chnu.awards.award.event.ReviewTakenOver;
import ua.edu.chnu.awards.award.service.AwardLinks;
import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.notification.entity.NotificationChannel;
import ua.edu.chnu.awards.notification.entity.NotificationType;
import ua.edu.chnu.awards.notification.service.NotificationPreferenceService;

import lombok.RequiredArgsConstructor;

/**
 * Tells a reviewer that a colleague took over a request they held, once the take-over commits.
 */
@Component
@RequiredArgsConstructor
public class TakeOverMails {

    public static final String SUBJECT = "Заявку передано іншому рецензенту / A request was taken over";

    private final MailDelivery delivery;
    private final AwardLinks links;
    private final NotificationPreferenceService preferences;

    /**
     * Sends the previous holder one message about the take-over.
     *
     * @param event the committed take-over
     */
    @Async
    @TransactionalEventListener
    public void onTakenOver(ReviewTakenOver event) {
        if (event.email() == null
            || !preferences.allows(event.userId(), NotificationType.REVIEW_TAKEN_OVER, NotificationChannel.EMAIL)) {
            return;
        }
        delivery.send(event.email(), SUBJECT, body(event, links.awardLink(event.awardId())));
    }

    static String body(ReviewTakenOver event, String link) {
        return helloUk(event.firstName())
            + event.reviewer() + " взяв(ла) на себе розгляд заявки «" + event.titleUk()
            + "», яку розглядали ви. Від вас більше нічого не потрібно.\n"
            + "Переглянути нагороду: " + link + "\n\n"
            + SEPARATOR
            + helloEn(event.firstName())
            + event.reviewer() + " took over the review of \"" + event.title()
            + "\", which you were reviewing. Nothing more is needed from you.\n"
            + "View the award: " + link + "\n";
    }
}
