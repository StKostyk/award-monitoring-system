package ua.edu.chnu.awards.notification;

import static ua.edu.chnu.awards.common.mail.MailTextHelper.SEPARATOR;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloEn;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloUk;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import ua.edu.chnu.awards.award.event.PersonInvited;
import ua.edu.chnu.awards.award.service.AwardLinks;
import ua.edu.chnu.awards.common.mail.MailDelivery;

import lombok.RequiredArgsConstructor;

/**
 * Sends a person without an account the invitation to register, which is also the notice about the data a
 * colleague entered about them (GDPR Art. 14), once the transaction that added or invited the person commits.
 */
@Component
@RequiredArgsConstructor
public class InvitationMails {

    public static final String SUBJECT = "Вас додано до реєстру нагород ЧНУ / You were added to the CHNU award record";

    private final MailDelivery delivery;
    private final AwardLinks links;

    /**
     * Sends the invitation.
     *
     * @param event the committed invitation
     */
    @Async
    @TransactionalEventListener
    public void onInvited(PersonInvited event) {
        delivery.send(event.email(), SUBJECT,
            body(event, links.registrationLink(event.email()), links.privacyNoticeLink()));
    }

    static String body(PersonInvited event, String registration, String notice) {
        return helloUk(event.firstName())
            + event.addedBy() + " додав(ла) вас до системи обліку нагород Чернівецького національного університету"
            + " як працівника підрозділу «" + event.departmentUk() + "», щоб внести ваші нагороди.\n\n"
            + "Мета: повний облік нагород працівників університету. Підстава: законний інтерес університету"
            + " (ст. 6(1)(f) GDPR). Збережено ваше ім'я, адресу, підрозділ і внесені нагороди.\n"
            + "Ви маєте право заперечити проти обробки, отримати доступ до даних і вимагати їх видалення."
            + " Контакт відповідального за захист персональних даних: " + event.dataProtectionContact() + "\n"
            + "Повне повідомлення про конфіденційність: " + notice + "\n\n"
            + "Зареєструйтеся з цією адресою, щоб переглядати й доповнювати свої нагороди: " + registration + "\n\n"
            + SEPARATOR
            + helloEn(event.firstName())
            + event.addedBy() + " added you to the award record of Yuriy Fedkovych Chernivtsi National University"
            + " as a member of \"" + event.department() + "\", so your awards can be entered.\n\n"
            + "Purpose: a complete record of the university staff's awards. Legal basis: the university's legitimate"
            + " interest (GDPR Art. 6(1)(f)). Stored: your name, address, department and the awards entered.\n"
            + "You have the right to object to the processing, to access the data and to ask for its erasure."
            + " Data protection officer: " + event.dataProtectionContact() + "\n"
            + "Full privacy notice: " + notice + "\n\n"
            + "Register with this address to see and complete your awards: " + registration + "\n";
    }
}
