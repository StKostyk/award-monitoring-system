package ua.edu.chnu.awards.award.service;

import static ua.edu.chnu.awards.common.mail.MailTextHelper.SEPARATOR;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloEn;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloUk;

import java.util.Map;
import java.util.function.Function;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import ua.edu.chnu.awards.award.event.AwardCorrected;
import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.config.AuthProperties;

import lombok.RequiredArgsConstructor;

/**
 * Tells the owner of a pending award that a reviewer corrected it once the correction commits: which fields
 * changed from what to what, the reason and the reviewer's name.
 */
@Component
@RequiredArgsConstructor
public class CorrectionMails {

    static final String SUBJECT = "Рецензент виправив нагороду / A reviewer corrected your award";
    private static final String EMPTY = "—";
    private static final Map<String, String> LABELS_UK = Map.of(
        "title", "Назва (англ.)",
        "titleUk", "Назва",
        "description", "Опис (англ.)",
        "descriptionUk", "Опис",
        "categoryId", "Категорія",
        "awardingOrganization", "Організація, що нагородила",
        "awardDate", "Дата нагородження",
        "externalUrl", "Посилання");
    private static final Map<String, String> LABELS_EN = Map.of(
        "title", "Title",
        "titleUk", "Title (Ukrainian)",
        "description", "Description",
        "descriptionUk", "Description (Ukrainian)",
        "categoryId", "Category",
        "awardingOrganization", "Awarding organisation",
        "awardDate", "Award date",
        "externalUrl", "Link");

    private final MailDelivery delivery;
    private final AuthProperties properties;

    /**
     * Sends the owner one message about the correction.
     *
     * @param event the committed correction
     */
    @Async
    @TransactionalEventListener
    public void onCorrected(AwardCorrected event) {
        if (event.email() == null) {
            return;
        }
        delivery.send(event.email(), SUBJECT, body(event, properties.awardLink(event.awardId())));
    }

    static String body(AwardCorrected event, String link) {
        return helloUk(event.firstName())
            + event.reviewer() + " виправив(ла) нагороду «" + event.titleUk() + "»:\n"
            + changes(event, LABELS_UK, change -> pair(change.fromUk(), change.toUk()))
            + "Причина: " + event.reason() + "\n"
            + "Переглянути нагороду: " + link + "\n\n"
            + SEPARATOR
            + helloEn(event.firstName())
            + event.reviewer() + " corrected the award \"" + event.title() + "\":\n"
            + changes(event, LABELS_EN, change -> pair(change.from(), change.to()))
            + "Reason: " + event.reason() + "\n"
            + "View the award: " + link + "\n";
    }

    private static String changes(AwardCorrected event, Map<String, String> labels,
                                  Function<AwardCorrected.Change, String> values) {
        StringBuilder text = new StringBuilder();
        for (AwardCorrected.Change change : event.changes()) {
            text.append("- ").append(labels.getOrDefault(change.field(), change.field())).append(": ")
                .append(values.apply(change)).append('\n');
        }
        return text.toString();
    }

    private static String pair(String from, String to) {
        return (from == null ? EMPTY : from) + " → " + (to == null ? EMPTY : to);
    }
}
