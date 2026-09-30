package ua.edu.chnu.awards.gdpr.service;

import static ua.edu.chnu.awards.common.mail.MailTextHelper.SEPARATOR;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloEn;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloUk;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.gdpr.event.DataExported;

import lombok.RequiredArgsConstructor;

/**
 * Sends the notices about a person's data rights being exercised, after the transaction commits.
 */
@Component
@RequiredArgsConstructor
public class PrivacyMails {

    private final MailDelivery delivery;

    /**
     * Tells the account's address that its data was exported.
     *
     * @param event the committed export
     */
    @Async
    @TransactionalEventListener
    public void onDataExported(DataExported event) {
        delivery.send(event.email(), "Ваші дані експортовано / Your data was exported", exportBody(event));
    }

    static String exportBody(DataExported event) {
        String facts = "Час / Time: " + event.at() + "\n"
            + "IP: " + event.ip() + "\n"
            + "Браузер / Browser: " + event.browser() + "\n\n";
        return helloUk(event.firstName())
            + "Ваші дані з системи обліку нагород ЧНУ щойно завантажили у форматі JSON.\n\n"
            + facts
            + "Якщо це були не ви, негайно змініть пароль і зверніться до адміністратора системи.\n\n"
            + SEPARATOR
            + helloEn(event.firstName())
            + "Your data in the ChNU award monitoring system was just downloaded as a JSON file.\n\n"
            + facts
            + "If this was not you, change your password at once and contact the system administrator.\n";
    }
}
