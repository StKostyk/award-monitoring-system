package ua.edu.chnu.awards.authz;

import static ua.edu.chnu.awards.common.mail.MailTextHelper.SEPARATOR;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloEn;
import static ua.edu.chnu.awards.common.mail.MailTextHelper.helloUk;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.delegation.event.DelegationCreated;
import ua.edu.chnu.awards.delegation.event.DelegationRevoked;
import ua.edu.chnu.awards.user.event.RoleAssigned;
import ua.edu.chnu.awards.user.event.RoleRevoked;

import lombok.RequiredArgsConstructor;

/**
 * Tells users about granted and revoked roles and delegations after the change commits.
 */
@Component
@RequiredArgsConstructor
public class AuthorityChangeMails {

    private static final String IN_ORGANIZATION = "» у підрозділі «";
    private static final String IN_EN = " in ";

    private final MailDelivery delivery;

    /**
     * Tells the holder which role was granted, where and for how long.
     *
     * @param event the committed assignment
     */
    @Async
    @TransactionalEventListener
    public void onRoleAssigned(RoleAssigned event) {
        delivery.send(event.email(), "Роль призначено / Role assigned", roleAssignedBody(event));
    }

    /**
     * Tells the former holder the last day of the role.
     *
     * @param event the committed revocation
     */
    @Async
    @TransactionalEventListener
    public void onRoleRevoked(RoleRevoked event) {
        delivery.send(event.email(), "Роль відкликано / Role revoked", roleRevokedBody(event));
    }

    /**
     * Tells the delegate what authority was lent, for how long and why.
     *
     * @param event the committed delegation
     */
    @Async
    @TransactionalEventListener
    public void onDelegationCreated(DelegationCreated event) {
        delivery.send(event.email(), "Делеговано повноваження / Authority delegated", delegationCreatedBody(event));
    }

    /**
     * Tells each party of a delegation that somebody took it back.
     *
     * @param event the committed revocation
     */
    @Async
    @TransactionalEventListener
    public void onDelegationRevoked(DelegationRevoked event) {
        String text = delegationRevokedBody(event);
        event.recipients().forEach(recipient ->
            delivery.send(recipient, "Делегування відкликано / Delegation revoked", text));
    }

    static String roleAssignedBody(RoleAssigned event) {
        String until = event.validTo() == null ? "" : " до " + event.validTo();
        String untilEn = event.validTo() == null ? "" : " until " + event.validTo();
        return helloUk(event.firstName())
            + event.actor() + " призначив(ла) вам роль «" + event.role() + IN_ORGANIZATION
            + event.organizationUk() + "» з " + event.validFrom() + until + ".\n"
            + "Нові права з'являться після наступного входу до системи.\n\n"
            + SEPARATOR
            + helloEn(event.firstName())
            + event.actor() + " granted you the role \"" + event.role() + "\"" + IN_EN + event.organization()
            + " from " + event.validFrom() + untilEn + ".\n"
            + "The new permissions apply from your next sign-in.\n";
    }

    static String roleRevokedBody(RoleRevoked event) {
        return helloUk(event.firstName())
            + event.actor() + " відкликав(ла) вашу роль «" + event.role() + IN_ORGANIZATION
            + event.organizationUk() + "»; останній день дії — " + event.lastDay() + ".\n"
            + "Усі сеанси завершено, тож увійдіть до системи ще раз.\n\n"
            + SEPARATOR
            + helloEn(event.firstName())
            + event.actor() + " revoked your role \"" + event.role() + "\"" + IN_EN + event.organization()
            + "; its last day is " + event.lastDay() + ".\n"
            + "Every session was ended, so sign in again.\n";
    }

    static String delegationCreatedBody(DelegationCreated event) {
        String why = event.reason() == null ? "" : "Причина: " + event.reason() + "\n";
        String whyEn = event.reason() == null ? "" : "Reason: " + event.reason() + "\n";
        return helloUk(event.firstName())
            + event.delegator() + " делегував(ла) вам повноваження ролі «" + event.role() + IN_ORGANIZATION
            + event.organizationUk() + "» з " + event.validFrom() + " до " + event.validTo() + ".\n"
            + why
            + "Ви зможете переглядати й погоджувати нагороди цього підрозділу; керування користувачами не "
            + "передається. Повноваження з'являться після наступного входу до системи.\n\n"
            + SEPARATOR
            + helloEn(event.firstName())
            + event.delegator() + " delegated the authority of the role \"" + event.role() + "\"" + IN_EN
            + event.organization() + " to you from " + event.validFrom() + " until " + event.validTo() + ".\n"
            + whyEn
            + "You may read and approve the awards of that organisation; user management is not handed over. "
            + "The authority applies from your next sign-in.\n";
    }

    static String delegationRevokedBody(DelegationRevoked event) {
        return "Делегування повноважень ролі «" + event.role() + IN_ORGANIZATION + event.organizationUk()
            + "» для користувача " + event.delegate() + " відкликав(ла) " + event.actor() + ".\n"
            + "Усі сеанси делегата завершено.\n\n"
            + SEPARATOR
            + "The delegation of the role \"" + event.role() + "\"" + IN_EN + event.organization() + " to "
            + event.delegate() + " was revoked by " + event.actor() + ".\n"
            + "Every session of the delegate was ended.\n";
    }
}
