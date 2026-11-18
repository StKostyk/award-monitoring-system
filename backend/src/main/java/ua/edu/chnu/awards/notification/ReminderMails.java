package ua.edu.chnu.awards.notification;

import static ua.edu.chnu.awards.common.mail.MailTextHelper.SEPARATOR;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloEn;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloUk;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import ua.edu.chnu.awards.notification.event.UnitReminderRequested;
import ua.edu.chnu.awards.notification.service.DeliveryLog;
import ua.edu.chnu.awards.notification.service.NotificationDrafts;

import lombok.RequiredArgsConstructor;

/**
 * Reminds a colleague after the reminder commits that the department has no award this year, unless the
 * colleague switched these mails off.
 */
@Component
@RequiredArgsConstructor
public class ReminderMails {

    private final DeliveryLog deliveries;
    private final NotificationDrafts drafts;

    /**
     * Names the department, the year and the sender.
     *
     * @param event the committed reminder
     */
    @Async
    @TransactionalEventListener
    public void onUnitReminderRequested(UnitReminderRequested event) {
        deliveries.send(drafts.of(event), event.email(), "Нагадування про нагороди / Awards reminder",
            reminderBody(event));
    }

    static String reminderBody(UnitReminderRequested event) {
        return helloUk(event.firstName())
            + event.sender() + " нагадує, що підрозділ «" + event.unitUk() + "» ще не має жодної нагороди у "
            + event.year() + " році.\n"
            + "Якщо у вас або колег є нагороди цього року, внесіть їх до системи.\n\n"
            + SEPARATOR
            + helloEn(event.firstName())
            + event.sender() + " reminds you that " + event.unit() + " has no award in " + event.year() + " yet.\n"
            + "If you or your colleagues received awards this year, please add them to the system.\n";
    }
}
