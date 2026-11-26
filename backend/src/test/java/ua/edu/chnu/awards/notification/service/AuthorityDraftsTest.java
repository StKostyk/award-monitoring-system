package ua.edu.chnu.awards.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.delegation.event.DelegationRevoked;
import ua.edu.chnu.awards.notification.entity.NotificationType;
import ua.edu.chnu.awards.support.TestEvents;
import ua.edu.chnu.awards.user.event.RoleRevoked;

class AuthorityDraftsTest {

    private static final Instant AT = Instant.parse("2026-10-14T10:00:00Z");
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);
    private static final String EMAIL = "olena@chnu.edu.ua";
    private static final String OTHER_PARTY = "otherParty";

    private final NotificationDrafts drafts = new NotificationDrafts();

    @Test
    void ac1_2_rolesCarryRoleOrganisationAndLastDay() {
        NotificationDraft assigned = drafts.of(TestEvents.roleAssigned().to(7L, EMAIL, "Олена").at(AT).build());
        NotificationDraft revoked = drafts.of(new RoleRevoked(7L, EMAIL, "Олена", RoleType.DEAN, "FMI", "ФМІ",
            "Адміністратор", TO, AT));

        assertThat(assigned.type()).isEqualTo(NotificationType.ROLE_ASSIGNED);
        assertThat(assigned.payload()).containsOnlyKeys("role", "organization", "organizationUk", "validTo")
            .containsEntry("role", "DEAN").containsEntry("validTo", null);
        assertThat(revoked.type()).isEqualTo(NotificationType.ROLE_REVOKED);
        assertThat(revoked.payload()).containsEntry("validTo", "2026-12-31").containsEntry("organizationUk", "ФМІ");
        assertThat(revoked.referenceType()).isNull();
    }

    @Test
    void ac1_2_aDelegationNamesTheDelegatorAsTheOtherParty() {
        NotificationDraft draft = drafts.of(TestEvents.delegationCreated().to(7L, EMAIL, "Олена")
            .by("Марія Мартинюк", "Відпустка").valid(LocalDate.of(2026, 10, 1), TO).at(AT).build());

        assertThat(draft.type()).isEqualTo(NotificationType.DELEGATION_CREATED);
        assertThat(draft.payload()).containsEntry(OTHER_PARTY, "Марія Мартинюк").containsEntry("validTo",
            "2026-12-31").doesNotContainValue("Відпустка");
    }

    @Test
    void edgeCase_aRevokedDelegationGivesEachRecipientTheirOwnDraft() {
        List<NotificationDraft> revoked = drafts.of(TestEvents.delegationRevoked(
                new DelegationRevoked.Recipient(7L, EMAIL, "Марія Мартинюк"),
                new DelegationRevoked.Recipient(3L, "dean@chnu.edu.ua", "Олена Петренко"))
            .of("Олена Петренко", "Адміністратор").until(TO).at(AT).build());

        assertThat(revoked).extracting(NotificationDraft::userId).containsExactly(7L, 3L);
        assertThat(revoked).extracting(draft -> draft.payload().get(OTHER_PARTY))
            .containsExactly("Марія Мартинюк", "Олена Петренко");
        assertThat(revoked).allMatch(draft -> draft.type() == NotificationType.DELEGATION_REVOKED);
    }
}
