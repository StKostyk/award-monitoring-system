package ua.edu.chnu.awards.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.common.mail.MailContent;
import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.delegation.event.DelegationCreated;
import ua.edu.chnu.awards.delegation.event.DelegationRevoked;
import ua.edu.chnu.awards.notification.entity.NotificationChannel;
import ua.edu.chnu.awards.notification.entity.NotificationType;
import ua.edu.chnu.awards.notification.service.NotificationDrafts;
import ua.edu.chnu.awards.notification.service.NotificationPreferenceService;
import ua.edu.chnu.awards.support.TestDeliveries;
import ua.edu.chnu.awards.support.TestEvents;
import ua.edu.chnu.awards.user.event.RoleAssigned;
import ua.edu.chnu.awards.user.event.RoleRevoked;

class AuthorityChangeMailsTest {

    private static final String FMI = "Faculty of Mathematics and Informatics";
    private static final String FMI_UK = "Факультет математики та інформатики";

    private final MailDelivery delivery = mock(MailDelivery.class);
    private final NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
    private final AuthorityChangeMails mails = new AuthorityChangeMails(TestDeliveries.log(delivery, preferences),
        new NotificationDrafts());
    private final ArgumentCaptor<MailContent> text = ArgumentCaptor.forClass(MailContent.class);

    @BeforeEach
    void allowEveryChannel() {
        when(preferences.allows(any(), any(), any())).thenReturn(true);
    }

    @Test
    void ac2_5_tellsTheHolderWhichRoleWasGrantedWhereAndUntilWhen() {
        mails.onRoleAssigned(assigned(RoleType.FACULTY_SECRETARY, LocalDate.of(2026, 10, 22)));

        verify(delivery).send(eq("member@chnu.edu.ua"), eq("Роль призначено / Role assigned"), text.capture());
        assertThat(text.getValue().text()).contains("FACULTY_SECRETARY", FMI_UK, FMI, "Марія Мартинюк", "2026-09-22",
            "2026-10-22");
    }

    @Test
    void ac2_5_anOpenEndedRoleNamesNoEndDate() {
        mails.onRoleAssigned(assigned(RoleType.EMPLOYEE, null));

        verify(delivery).send(eq("member@chnu.edu.ua"), eq("Роль призначено / Role assigned"), text.capture());
        assertThat(text.getValue().text()).doesNotContain(" until ");
    }

    @Test
    void ac2_5_tellsTheFormerHolderTheLastDayAndThatEverySessionEnded() {
        mails.onRoleRevoked(new RoleRevoked(4L, "member@chnu.edu.ua", "Анастасія", RoleType.DEAN, FMI, FMI_UK,
            "Марія Мартинюк", LocalDate.of(2026, 9, 21), null));

        verify(delivery).send(eq("member@chnu.edu.ua"), eq("Роль відкликано / Role revoked"), text.capture());
        assertThat(text.getValue().text()).contains("DEAN", "2026-09-21", "Every session was ended");
    }

    @Test
    void ac3_5_tellsTheDelegateWhatWasLentForHowLongAndWhy() {
        mails.onDelegationCreated(delegation("Відпустка"));

        verify(delivery).send(eq("secretary@chnu.edu.ua"), eq("Делеговано повноваження / Authority delegated"),
            text.capture());
        assertThat(text.getValue().text()).contains("DEAN", FMI_UK, "Марія Мартинюк", "2026-09-24", "2026-10-08",
            "Відпустка", "user management is not handed over");
    }

    @Test
    void ac3_5_aDelegationWithoutAReasonLeavesTheLineOut() {
        mails.onDelegationCreated(delegation(null));

        verify(delivery).send(eq("secretary@chnu.edu.ua"), eq("Делеговано повноваження / Authority delegated"),
            text.capture());
        assertThat(text.getValue().text()).doesNotContain("Причина").doesNotContain("Reason");
    }

    @Test
    void ac3_5_tellsBothSidesWhenSomebodyElseTookTheAuthorityBack() {
        mails.onDelegationRevoked(revoked(DelegationRevoked.Recipient.of("secretary@chnu.edu.ua"),
            DelegationRevoked.Recipient.of("dean@chnu.edu.ua")));

        ArgumentCaptor<String> recipient = ArgumentCaptor.forClass(String.class);
        verify(delivery, times(2)).send(recipient.capture(), eq("Делегування відкликано / Delegation revoked"),
            text.capture());
        assertThat(recipient.getAllValues()).containsExactly("secretary@chnu.edu.ua", "dean@chnu.edu.ua");
        assertThat(text.getAllValues()).extracting(MailContent::text).allSatisfy(body -> assertThat(body)
            .contains("DEAN", "Аліна Коваленко", "Олег Адміненко", "Every session of the delegate was ended"));
    }

    @Test
    void ac3_5_onlyRecipientsWhoKeptTheseMailsOnAreTold() {
        when(preferences.allows(4L, NotificationType.ROLE_ASSIGNED, NotificationChannel.EMAIL)).thenReturn(false);
        when(preferences.allows(8L, NotificationType.DELEGATION_REVOKED, NotificationChannel.EMAIL)).thenReturn(false);

        mails.onRoleAssigned(assigned(RoleType.EMPLOYEE, null));
        mails.onDelegationRevoked(revoked(new DelegationRevoked.Recipient(8L, "secretary@chnu.edu.ua", null),
            new DelegationRevoked.Recipient(9L, "dean@chnu.edu.ua", null)));

        verify(delivery, never()).send(eq("member@chnu.edu.ua"), anyString(), any(MailContent.class));
        verify(delivery, never()).send(eq("secretary@chnu.edu.ua"), anyString(), any(MailContent.class));
        verify(delivery).send(eq("dean@chnu.edu.ua"), eq("Делегування відкликано / Delegation revoked"),
            any(MailContent.class));
    }

    private static DelegationCreated delegation(String reason) {
        return TestEvents.delegationCreated().to(5L, "secretary@chnu.edu.ua", "Аліна")
            .role(RoleType.DEAN, FMI, FMI_UK).by("Марія Мартинюк", reason)
            .valid(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 10, 8)).build();
    }

    private static RoleAssigned assigned(RoleType role, LocalDate validTo) {
        return TestEvents.roleAssigned().to(4L, "member@chnu.edu.ua", "Анастасія").role(role, FMI, FMI_UK)
            .by("Марія Мартинюк").valid(LocalDate.of(2026, 9, 22), validTo).build();
    }

    private static DelegationRevoked revoked(DelegationRevoked.Recipient... recipients) {
        return TestEvents.delegationRevoked(recipients).role(RoleType.DEAN, FMI, FMI_UK)
            .of("Аліна Коваленко", "Олег Адміненко").build();
    }
}
