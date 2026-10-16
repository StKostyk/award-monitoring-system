package ua.edu.chnu.awards.gdpr.service;

import static ua.edu.chnu.awards.common.mail.MailTextHelper.SEPARATOR;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloEn;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloUk;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.gdpr.event.DataExported;
import ua.edu.chnu.awards.gdpr.event.ErasureCancelled;
import ua.edu.chnu.awards.gdpr.event.ErasureCompleted;
import ua.edu.chnu.awards.gdpr.event.ErasurePostponed;
import ua.edu.chnu.awards.gdpr.event.ErasureRequested;
import ua.edu.chnu.awards.gdpr.event.ErasureWarned;

import lombok.RequiredArgsConstructor;

/**
 * Sends the notices about a person's data rights being exercised, after the transaction commits.
 */
@Component
@RequiredArgsConstructor
public class PrivacyMails {

    private static final String SENTENCE_END = ".\n\n";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final MailDelivery delivery;
    private final PrivacyLinks links;
    private final Clock clock;

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

    /**
     * Tells the person when the account is erased and how to cancel.
     *
     * @param event the committed request
     */
    @Async
    @TransactionalEventListener
    public void onErasureRequested(ErasureRequested event) {
        delivery.send(event.email(), "Запит на видалення облікового запису / Account erasure requested",
            requestedBody(event, date(event.scheduledFor()), links.privacyCentre()));
    }

    /**
     * Reminds the person that the erasure is near.
     *
     * @param event the committed reminder
     */
    @Async
    @TransactionalEventListener
    public void onErasureWarned(ErasureWarned event) {
        delivery.send(event.email(), "Обліковий запис скоро буде видалено / Your account will be erased soon",
            warningBody(event, date(event.scheduledFor()), links.privacyCentre()));
    }

    /**
     * Confirms that the erasure was cancelled.
     *
     * @param event the committed cancellation
     */
    @Async
    @TransactionalEventListener
    public void onErasureCancelled(ErasureCancelled event) {
        delivery.send(event.email(), "Видалення скасовано / Account erasure cancelled", cancelledBody(event));
    }

    /**
     * Tells the person that the erasure waits for a legal obligation to end.
     *
     * @param event the committed postponement
     */
    @Async
    @TransactionalEventListener
    public void onErasurePostponed(ErasurePostponed event) {
        delivery.send(event.email(), "Видалення відкладено / Account erasure postponed",
            postponedBody(event, links.privacyCentre()));
    }

    /**
     * Confirms the erasure at the address the account had before it.
     *
     * @param event the committed erasure
     */
    @Async
    @TransactionalEventListener
    public void onErasureCompleted(ErasureCompleted event) {
        delivery.send(event.email(), "Обліковий запис видалено / Your account was erased", completedBody(event));
    }

    static String requestedBody(ErasureRequested event, String date, String link) {
        return helloUk(event.firstName())
            + "Ви попросили видалити ваш обліковий запис у системі обліку нагород ЧНУ. Його буде видалено "
            + date + SENTENCE_END
            + "Затверджені нагороди залишаться під псевдонімом, записи журналу аудиту буде знеособлено, а записи "
            + "згод зберігаються сім років.\n\n"
            + "Скасувати видалення до цієї дати можна в центрі конфіденційності: " + link + "\n\n"
            + "Якщо це були не ви, скасуйте видалення, негайно змініть пароль і зверніться до адміністратора "
            + "системи.\n\n"
            + SEPARATOR
            + helloEn(event.firstName())
            + "You asked to erase your account in the ChNU award monitoring system. It will be erased on "
            + date + SENTENCE_END
            + "Approved awards stay under a pseudonym, audit records are redacted, and consent records are kept for "
            + "seven years.\n\n"
            + "You can cancel the erasure before that date in the privacy centre: " + link + "\n\n"
            + "If this was not you, cancel the erasure, change your password at once and contact the system "
            + "administrator.\n";
    }

    static String warningBody(ErasureWarned event, String date, String link) {
        return helloUk(event.firstName())
            + "Ваш обліковий запис у системі обліку нагород ЧНУ буде видалено " + date + SENTENCE_END
            + "Скасувати видалення можна в центрі конфіденційності: " + link + "\n\n"
            + SEPARATOR
            + helloEn(event.firstName())
            + "Your account in the ChNU award monitoring system will be erased on " + date + SENTENCE_END
            + "You can cancel the erasure in the privacy centre: " + link + "\n";
    }

    static String cancelledBody(ErasureCancelled event) {
        return helloUk(event.firstName())
            + "Видалення вашого облікового запису в системі обліку нагород ЧНУ скасовано. Обліковий запис "
            + "залишається активним.\n\n"
            + "Якщо це були не ви, негайно змініть пароль і зверніться до адміністратора системи.\n\n"
            + SEPARATOR
            + helloEn(event.firstName())
            + "The erasure of your account in the ChNU award monitoring system is cancelled. The account stays "
            + "active.\n\n"
            + "If this was not you, change your password at once and contact the system administrator.\n";
    }

    static String postponedBody(ErasurePostponed event, String link) {
        return helloUk(event.firstName())
            + "Видалення вашого облікового запису в системі обліку нагород ЧНУ відкладено: університет має "
            + "зберігати ваші дані, доки діє правовий обов'язок. Обліковий запис буде видалено, щойно цей "
            + "обов'язок припиниться.\n\n"
            + "Звернутися до відповідального за захист персональних даних можна через центр конфіденційності: "
            + link + "\n\n"
            + SEPARATOR
            + helloEn(event.firstName())
            + "The erasure of your account in the ChNU award monitoring system is postponed: the university must "
            + "keep your data while a legal obligation applies. The account will be erased as soon as that "
            + "obligation ends.\n\n"
            + "You can reach the data protection officer through the privacy centre: " + link + "\n";
    }

    static String completedBody(ErasureCompleted event) {
        return helloUk(event.firstName())
            + "Ваш обліковий запис у системі обліку нагород ЧНУ видалено. Увійти в нього більше не можна.\n\n"
            + "Затверджені нагороди залишаються під псевдонімом без документів, записи журналу аудиту "
            + "знеособлено, а записи згод зберігаються сім років.\n\n"
            + SEPARATOR
            + helloEn(event.firstName())
            + "Your account in the ChNU award monitoring system was erased. It can no longer be signed in to.\n\n"
            + "Approved awards stay under a pseudonym without their documents, audit records are redacted, and "
            + "consent records are kept for seven years.\n";
    }

    private String date(Instant at) {
        return DATE.withZone(clock.getZone()).format(at);
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
