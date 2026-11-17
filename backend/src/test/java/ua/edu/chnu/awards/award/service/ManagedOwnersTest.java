package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.entity.OrganizationType;
import ua.edu.chnu.awards.support.TestAwards;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.service.UserAccounts;

class ManagedOwnersTest {

    private static final long SECRETARY_ID = 31L;

    private final UserRepository users = mock(UserRepository.class);
    private final RecipientUnits units = mock(RecipientUnits.class);
    private final ManagedOwners owners = new ManagedOwners(new UserAccounts(users), units);
    private final Organization department = TestUsers.organization(64L, OrganizationType.DEPARTMENT);
    private final Organization elsewhere = TestUsers.organization(70L, OrganizationType.DEPARTMENT);
    private final User secretary = TestUsers.person(SECRETARY_ID, "secretary@chnu.edu.ua", department);

    @BeforeEach
    void setUp() {
        when(units.covers(64L)).thenReturn(true);
    }

    @Test
    void ac2_3_aPersonWithoutAnAccountInScopeIsManaged() {
        User unclaimed = person(40L, AccountStatus.UNCLAIMED, department);
        User pending = person(41L, AccountStatus.PENDING, department);
        when(users.findById(40L)).thenReturn(Optional.of(unclaimed));

        assertThat(owners.manages(unclaimed)).isTrue();
        assertThat(owners.manages(pending)).isTrue();
        assertThat(owners.find(40L)).contains(unclaimed);
    }

    @Test
    void ac2_2_anAccountAnErasedPersonOrAnotherScopeIsNotManaged() {
        User active = TestUsers.person(42L, "employee@chnu.edu.ua", department);
        User erased = person(43L, AccountStatus.DELETED, department);
        User outside = person(44L, AccountStatus.UNCLAIMED, elsewhere);
        when(users.findById(44L)).thenReturn(Optional.of(outside));
        when(users.findById(45L)).thenReturn(Optional.empty());

        assertThat(owners.manages(active)).isFalse();
        assertThat(owners.manages(erased)).isFalse();
        assertThat(owners.find(44L)).isEmpty();
        assertThat(owners.find(45L)).isEmpty();
    }

    @Test
    void ac2_5_noticesOfAPersonsAwardGoToTheSubmitterAndNameThePerson() {
        Award award = TestAwards.award(person(40L, AccountStatus.UNCLAIMED, department), department).build();
        AwardRequest request = TestAwards.request(award).submitter(secretary).build();
        Award own = TestAwards.award(secretary, department).build();

        assertThat(NoticeRecipientHelper.recipient(request)).isSameAs(secretary);
        assertThat(NoticeRecipientHelper.person(award)).isEqualTo("Тестовий Петро");
        assertThat(NoticeRecipientHelper.recipient(TestAwards.request(own).build())).isSameAs(secretary);
        assertThat(NoticeRecipientHelper.person(own)).isNull();
    }

    private static User person(long id, AccountStatus status, Organization organization) {
        return User.builder().id(id).emailAddress("person" + id + "@chnu.edu.ua").firstName("Петро")
            .lastName("Тестовий").accountStatus(status).addedById(SECRETARY_ID).organization(organization).build();
    }
}
