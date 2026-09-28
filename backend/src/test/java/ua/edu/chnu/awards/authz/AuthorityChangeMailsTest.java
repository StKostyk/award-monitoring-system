package ua.edu.chnu.awards.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.delegation.event.DelegationCreated;
import ua.edu.chnu.awards.delegation.event.DelegationRevoked;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.event.RoleAssigned;
import ua.edu.chnu.awards.user.event.RoleRevoked;

class AuthorityChangeMailsTest {

    private static final String FMI = "Faculty of Mathematics and Informatics";
    private static final String FMI_UK = "Факультет математики та інформатики";

    private final MailDelivery delivery = mock(MailDelivery.class);
    private final AuthorityChangeMails mails = new AuthorityChangeMails(delivery);
    private final ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);

    @Test
    void ac2_5_tellsTheHolderWhichRoleWasGrantedWhereAndUntilWhen() {
        mails.onRoleAssigned(new RoleAssigned("member@chnu.edu.ua", "Анастасія", RoleType.FACULTY_SECRETARY,
            FMI, FMI_UK, "Марія Мартинюк", LocalDate.of(2026, 9, 22), LocalDate.of(2026, 10, 22)));

        verify(delivery).send(eq("member@chnu.edu.ua"), eq("Роль призначено / Role assigned"), text.capture());
        assertThat(text.getValue()).contains("FACULTY_SECRETARY", FMI_UK, FMI, "Марія Мартинюк", "2026-09-22",
            "2026-10-22");
    }

    @Test
    void ac2_5_anOpenEndedRoleNamesNoEndDate() {
        mails.onRoleAssigned(new RoleAssigned("member@chnu.edu.ua", "Анастасія", RoleType.EMPLOYEE,
            FMI, FMI_UK, "Марія Мартинюк", LocalDate.of(2026, 9, 22), null));

        verify(delivery).send(eq("member@chnu.edu.ua"), eq("Роль призначено / Role assigned"), text.capture());
        assertThat(text.getValue()).doesNotContain(" until ");
    }

    @Test
    void ac2_5_tellsTheFormerHolderTheLastDayAndThatEverySessionEnded() {
        mails.onRoleRevoked(new RoleRevoked("member@chnu.edu.ua", "Анастасія", RoleType.DEAN, FMI, FMI_UK,
            "Марія Мартинюк", LocalDate.of(2026, 9, 21)));

        verify(delivery).send(eq("member@chnu.edu.ua"), eq("Роль відкликано / Role revoked"), text.capture());
        assertThat(text.getValue()).contains("DEAN", "2026-09-21", "Every session was ended");
    }

    @Test
    void ac3_5_tellsTheDelegateWhatWasLentForHowLongAndWhy() {
        mails.onDelegationCreated(delegation("Відпустка"));

        verify(delivery).send(eq("secretary@chnu.edu.ua"), eq("Делеговано повноваження / Authority delegated"),
            text.capture());
        assertThat(text.getValue()).contains("DEAN", FMI_UK, "Марія Мартинюк", "2026-09-24", "2026-10-08",
            "Відпустка", "user management is not handed over");
    }

    @Test
    void ac3_5_aDelegationWithoutAReasonLeavesTheLineOut() {
        mails.onDelegationCreated(delegation(null));

        verify(delivery).send(eq("secretary@chnu.edu.ua"), eq("Делеговано повноваження / Authority delegated"),
            text.capture());
        assertThat(text.getValue()).doesNotContain("Причина").doesNotContain("Reason");
    }

    @Test
    void ac3_5_tellsBothSidesWhenSomebodyElseTookTheAuthorityBack() {
        mails.onDelegationRevoked(new DelegationRevoked(List.of("secretary@chnu.edu.ua", "dean@chnu.edu.ua"),
            RoleType.DEAN, FMI, FMI_UK, "Аліна Коваленко", "Олег Адміненко"));

        ArgumentCaptor<String> recipient = ArgumentCaptor.forClass(String.class);
        verify(delivery, times(2)).send(recipient.capture(), eq("Делегування відкликано / Delegation revoked"),
            text.capture());
        assertThat(recipient.getAllValues()).containsExactly("secretary@chnu.edu.ua", "dean@chnu.edu.ua");
        assertThat(text.getAllValues()).allSatisfy(body -> assertThat(body)
            .contains("DEAN", "Аліна Коваленко", "Олег Адміненко", "Every session of the delegate was ended"));
    }

    private static DelegationCreated delegation(String reason) {
        return new DelegationCreated("secretary@chnu.edu.ua", "Аліна", RoleType.DEAN, FMI, FMI_UK,
            "Марія Мартинюк", LocalDate.of(2026, 9, 24), LocalDate.of(2026, 10, 8), reason);
    }
}
