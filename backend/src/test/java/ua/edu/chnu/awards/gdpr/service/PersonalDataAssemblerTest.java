package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.auth.entity.UserDevice;
import ua.edu.chnu.awards.auth.repository.UserDeviceRepository;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardCategory;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.delegation.entity.DelegationState;
import ua.edu.chnu.awards.delegation.entity.RoleDelegation;
import ua.edu.chnu.awards.delegation.repository.RoleDelegationRepository;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.ActivityEntry;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.DelegationEntry;
import ua.edu.chnu.awards.gdpr.mapper.PersonalDataMapper;
import ua.edu.chnu.awards.gdpr.repository.PersonalDataQueries;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

class PersonalDataAssemblerTest {

    private static final Instant NOW = Instant.parse("2026-09-30T09:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);

    private final UserRoleRepository roles = mock(UserRoleRepository.class);
    private final RoleDelegationRepository delegations = mock(RoleDelegationRepository.class);
    private final AwardRepository awards = mock(AwardRepository.class);
    private final UserDeviceRepository devices = mock(UserDeviceRepository.class);
    private final PersonalDataQueries queries = mock(PersonalDataQueries.class);
    private final PersonalDataAssembler assembler = new PersonalDataAssembler(roles, delegations, awards, devices,
        queries, new PersonalDataMapper(), Clock.fixed(NOW, ZoneId.of("Europe/Kyiv")));

    private Organization faculty;
    private Organization department;
    private User user;

    @BeforeEach
    void setUp() {
        faculty = TestUsers.organization(9L, OrganizationType.FACULTY, "FMI");
        department = TestUsers.organization(64L, OrganizationType.DEPARTMENT, "DAI");
        department.setParent(faculty);
        user = TestUsers.person(5L, "employee.fmi@chnu.edu.ua", department);
        user.setPasswordHash("$2a$12$secret");
    }

    @Test
    void ac32_theMetadataAndProfileDescribeThePersonWithDepartmentAndFaculty() {
        PersonalDataFile file = assembler.assemble(user);

        assertThat(file.exportMetadata()).isEqualTo(new PersonalDataFile.Metadata(NOW, 5L, "1.0",
            "Article 20 - Right to Data Portability"));
        PersonalDataFile.Profile profile = file.personalData().profile();
        assertThat(profile.email()).isEqualTo("employee.fmi@chnu.edu.ua");
        assertThat(profile.department()).isEqualTo(new PersonalDataFile.NamedRef(64L, "DAI", "DAI (укр)"));
        assertThat(profile.faculty().id()).isEqualTo(9L);
    }

    @Test
    void ac32_edge_aPersonWithoutAnythingGetsEmptySectionsNotMissingOnes() {
        user.setOrganization(TestUsers.organization(1L, OrganizationType.UNIVERSITY));

        PersonalDataFile file = assembler.assemble(user);

        assertThat(file.personalData().profile().faculty()).isNull();
        assertThat(file.roles()).isEmpty();
        assertThat(file.delegations()).isEmpty();
        assertThat(file.awards()).isEmpty();
        assertThat(file.documents()).isEmpty();
        assertThat(file.consentHistory()).isEmpty();
        assertThat(file.devices()).isEmpty();
        assertThat(file.activityLog()).isEmpty();
        assertThat(file.sectionCounts()).containsOnlyKeys("roles", "delegations", "awards", "documents",
            "consent_history", "devices", "activity_log").allSatisfy((key, count) -> assertThat(count).isEqualTo(0));
    }

    @Test
    void ac32_rolesPastAndCurrentAreMarked() {
        when(roles.findHistoryByUserId(5L)).thenReturn(List.of(
            TestUsers.role(user, RoleType.EMPLOYEE, department, TODAY.minusYears(1), null),
            TestUsers.role(user, RoleType.DEAN, department, TODAY.minusYears(2), TODAY.minusYears(1))));

        List<PersonalDataFile.RoleEntry> entries = assembler.assemble(user).roles();

        assertThat(entries).extracting(PersonalDataFile.RoleEntry::role, PersonalDataFile.RoleEntry::current)
            .containsExactly(tuple(RoleType.EMPLOYEE, true),
                tuple(RoleType.DEAN, false));
    }

    @Test
    void ac32_ac33_delegationsNameTheOtherPartyOnlyAndKeepOnlyTheOwnReason() {
        User secretary = TestUsers.person(7L, "secretary@chnu.edu.ua", "Оксана", department);
        secretary.setPasswordHash("$2a$12$other");
        User dean = TestUsers.person(8L, "dean@chnu.edu.ua", "Петро", faculty);
        when(delegations.findByDelegatorId(5L)).thenReturn(List.of(delegation(user, secretary, "Відпустка")));
        when(delegations.findByDelegateId(5L)).thenReturn(List.of(delegation(dean, user, "Відрядження декана")));

        List<DelegationEntry> entries = assembler.assemble(user).delegations();

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).direction()).isEqualTo("GIVEN");
        assertThat(entries.get(0).otherParty()).isEqualTo("Оксана Мартинюк");
        assertThat(entries.get(0).reason()).isEqualTo("Відпустка");
        assertThat(entries.get(0).state()).isEqualTo(DelegationState.ACTIVE);
        assertThat(entries.get(1).direction()).isEqualTo("RECEIVED");
        assertThat(entries.get(1).otherParty()).isEqualTo("Петро Мартинюк");
        assertThat(entries.get(1).reason()).isNull();
        assertThat(entries.toString()).doesNotContain("secretary@chnu.edu.ua", "dean@chnu.edu.ua", "$2a$");
    }

    @Test
    void ac32_awardsDevicesAndQueriedSectionsAreCopied() {
        AwardCategory category = AwardCategory.builder().id(13L).name("Ministry").nameUk("Міністерство").build();
        when(awards.findByOwnerIdOrderByCreatedAtDescIdDesc(5L)).thenReturn(List.of(
            Award.builder().id(21L).owner(user).organization(department).category(category).title("Letter")
                .titleUk("Лист").status(AwardStatus.DRAFT).build(),
            Award.builder().id(22L).owner(user).organization(department).status(AwardStatus.PENDING).build()));
        when(devices.findByUserIdOrderByLastUsedAtDesc(5L)).thenReturn(List.of(UserDevice.builder().user(user)
            .fingerprint("f".repeat(64)).browser("Firefox").operatingSystem("Windows").lastIpAddress("10.0.0.7")
            .firstSeenAt(NOW).lastUsedAt(NOW).build()));
        when(queries.activity(anyLong(), anyCollection(), anyCollection()))
            .thenReturn(List.of(new ActivityEntry("LOGIN_SUCCESS", NOW, "10.0.0.7")));

        PersonalDataFile file = assembler.assemble(user);

        assertThat(file.awards()).extracting(PersonalDataFile.AwardEntry::awardId).containsExactly(21L, 22L);
        assertThat(file.awards().get(0).category().nameUk()).isEqualTo("Міністерство");
        assertThat(file.awards().get(1).category()).isNull();
        assertThat(file.devices().get(0).lastIpAddress()).isEqualTo("10.0.0.7");
        assertThat(file.devices().toString()).doesNotContain("ffff");
        assertThat(file.activityLog()).hasSize(1);
        assertThat(PersonalDataAssembler.ACTIVITY_AREAS).containsExactly("AUTHENTICATION", "AUTHORIZATION",
            "USER", "GDPR");
        assertThat(PersonalDataAssembler.SELF_ACTIONS).contains("LOGIN_SUCCESS", "PROFILE_UPDATED")
            .doesNotContain("LOGIN_FAILED", "ACCOUNT_LOCKED", "PASSWORD_RESET_REQUESTED", "ROLE_ASSIGNED");
    }

    private RoleDelegation delegation(User delegator, User delegate, String reason) {
        return RoleDelegation.builder().delegator(delegator).delegate(delegate).roleType(RoleType.DEAN)
            .organization(department).validFrom(TODAY.minusDays(1)).validTo(TODAY.plusDays(5)).reason(reason)
            .createdAt(NOW).build();
    }
}
