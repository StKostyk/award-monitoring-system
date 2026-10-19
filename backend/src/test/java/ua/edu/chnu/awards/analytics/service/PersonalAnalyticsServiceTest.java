package ua.edu.chnu.awards.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.analytics.config.AnalyticsProperties;
import ua.edu.chnu.awards.analytics.dto.PersonalAnalytics;
import ua.edu.chnu.awards.analytics.dto.PersonalRank;
import ua.edu.chnu.awards.analytics.dto.RankStatus;
import ua.edu.chnu.awards.analytics.dto.YearCount;
import ua.edu.chnu.awards.award.dto.CategoryRef;
import ua.edu.chnu.awards.award.dto.CategoryYearCount;
import ua.edu.chnu.awards.award.dto.CountedTotals;
import ua.edu.chnu.awards.award.dto.DepartmentStanding;
import ua.edu.chnu.awards.award.dto.OpenAwards;
import ua.edu.chnu.awards.award.dto.TimelineEntry;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.award.service.AwardStatistics;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.gdpr.ConsentLookup;
import ua.edu.chnu.awards.gdpr.dto.ConsentType;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;

@ExtendWith(MockitoExtension.class)
class PersonalAnalyticsServiceTest {

    private static final long USER_ID = 7L;
    private static final long DEPARTMENT_ID = 64L;
    private static final int MIN_GROUP_SIZE = 5;
    private static final int TIMELINE_LIMIT = 3;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneId.of("Europe/Kyiv"));
    private static final CategoryRef MINISTRY = new CategoryRef(13L, "Ministry Recognition", "Відзнака міністерства",
        RecognitionLevel.NATIONAL);
    private static final CategoryRef BEST_TEACHER = new CategoryRef(22L, "Best Teacher Award",
        "Нагорода найкращого викладача", RecognitionLevel.UNIVERSITY);
    private static final CategoryRef FACULTY_AWARD = new CategoryRef(31L, "Faculty Teaching Award",
        "Факультетська педагогічна нагорода", RecognitionLevel.FACULTY);

    @Mock
    private AwardStatistics statistics;

    @Mock
    private ConsentLookup consents;

    @Mock
    private UserAccounts users;

    private PersonalAnalyticsService service;

    @BeforeEach
    void setUp() {
        AnalyticsProperties properties = new AnalyticsProperties(MIN_GROUP_SIZE, TIMELINE_LIMIT);
        service = new PersonalAnalyticsService(statistics,
            new DepartmentRanking(statistics, consents, users, properties), properties, CLOCK);
    }

    @Test
    void ac1_2_totalsAndOpenAwardsComeFromTheCountedAwards() {
        YearRange period = new YearRange(2021, 2026);
        when(statistics.firstYear(USER_ID)).thenReturn(OptionalInt.of(2021));
        when(statistics.totals(USER_ID, period)).thenReturn(new CountedTotals(4, 1, LocalDate.of(2021, 3, 1),
            LocalDate.of(2026, 5, 2)));
        when(statistics.byCategoryAndYear(USER_ID, period)).thenReturn(List.of(
            new CategoryYearCount(2021, FACULTY_AWARD, 1), new CategoryYearCount(2023, MINISTRY, 2),
            new CategoryYearCount(2026, BEST_TEACHER, 1)));
        when(statistics.openAwards(USER_ID)).thenReturn(new OpenAwards(2, 1));
        withoutConsent();

        PersonalAnalytics analytics = service.of(USER_ID, null, null);

        assertThat(analytics.period()).isEqualTo(period);
        assertThat(analytics.totals().approved()).isEqualTo(4);
        assertThat(analytics.totals().verified()).isEqualTo(1);
        assertThat(analytics.totals().highestLevel()).isEqualTo(RecognitionLevel.NATIONAL);
        assertThat(analytics.totals().firstAwardDate()).isEqualTo(LocalDate.of(2021, 3, 1));
        assertThat(analytics.totals().latestAwardDate()).isEqualTo(LocalDate.of(2026, 5, 2));
        assertThat(analytics.inProgress()).isEqualTo(new OpenAwards(2, 1));
    }

    @Test
    void ac1_3_withoutCountedAwardsThePeriodIsTheCurrentYear() {
        YearRange period = new YearRange(2026, 2026);
        when(statistics.firstYear(USER_ID)).thenReturn(OptionalInt.empty());
        when(statistics.totals(USER_ID, period)).thenReturn(new CountedTotals(0, 0, null, null));
        when(statistics.openAwards(USER_ID)).thenReturn(new OpenAwards(1, 0));
        withoutConsent();

        PersonalAnalytics analytics = service.of(USER_ID, null, null);

        assertThat(analytics.period()).isEqualTo(period);
        assertThat(analytics.byYear()).extracting(YearCount::year, YearCount::total).containsExactly(tuple(2026, 0L));
        assertThat(analytics.totals().highestLevel()).isNull();
        assertThat(analytics.byLevel()).isEmpty();
    }

    @Test
    void ac4_6_onNewYearsEveInUtcThePeriodEndsInTheNewKyivYear() {
        YearRange period = new YearRange(2023, 2027);
        when(statistics.firstYear(USER_ID)).thenReturn(OptionalInt.of(2023));
        when(statistics.totals(USER_ID, period)).thenReturn(new CountedTotals(0, 0, null, null));
        when(statistics.openAwards(USER_ID)).thenReturn(new OpenAwards(0, 0));
        withoutConsent();
        Clock newYearInKyiv = Clock.fixed(Instant.parse("2026-12-31T22:30:00Z"), ZoneId.of("Europe/Kyiv"));
        AnalyticsProperties properties = new AnalyticsProperties(MIN_GROUP_SIZE, TIMELINE_LIMIT);
        PersonalAnalyticsService atNewYear = new PersonalAnalyticsService(statistics,
            new DepartmentRanking(statistics, consents, users, properties), properties, newYearInKyiv);

        PersonalAnalytics analytics = atNewYear.of(USER_ID, null, null);

        assertThat(analytics.period()).isEqualTo(period);
        assertThat(analytics.byYear()).extracting(YearCount::year).last().isEqualTo(2027);
    }

    @Test
    void ac1_3_anEndBeforeTheFirstAwardStartsThePeriodThere() {
        when(statistics.firstYear(USER_ID)).thenReturn(OptionalInt.of(2023));
        when(statistics.totals(USER_ID, new YearRange(2020, 2020))).thenReturn(new CountedTotals(0, 0, null, null));
        when(statistics.openAwards(USER_ID)).thenReturn(new OpenAwards(0, 0));
        withoutConsent();

        assertThat(service.of(USER_ID, null, 2020).period()).isEqualTo(new YearRange(2020, 2020));
    }

    @Test
    void ac1_4_theTimelineIsCutAtTheLimit() {
        YearRange period = new YearRange(2020, 2026);
        List<TimelineEntry> entries = IntStream.rangeClosed(1, TIMELINE_LIMIT + 1)
            .mapToObj(day -> new TimelineEntry((long) day, "Letter " + day, null, LocalDate.of(2026, 1, day),
                MINISTRY, "МОН", false))
            .toList();
        when(statistics.totals(USER_ID, period)).thenReturn(new CountedTotals(TIMELINE_LIMIT + 1, 0, null, null));
        when(statistics.timeline(USER_ID, period, TIMELINE_LIMIT + 1)).thenReturn(entries);
        when(statistics.openAwards(USER_ID)).thenReturn(new OpenAwards(0, 0));
        withoutConsent();

        PersonalAnalytics analytics = service.of(USER_ID, 2020, 2026);

        assertThat(analytics.timeline()).hasSize(TIMELINE_LIMIT).containsExactlyElementsOf(
            entries.subList(0, TIMELINE_LIMIT));
        assertThat(analytics.timelineTruncated()).isTrue();
    }

    @Test
    void ac1_5_yearsOutsideTheRangeNameTheParameter() {
        assertInvalid(1949, null, "from");
        assertInvalid(2101, null, "from");
        assertInvalid(null, 1949, "to");
        assertInvalid(null, 2101, "to");
        assertInvalid(2024, 2023, "from");
        assertInvalid(2027, null, "from");
        verify(statistics, never()).totals(anyLong(), any());
    }

    @Test
    void ac1_6_competitionRankingSharesThePositionOfEqualCounts() {
        YearRange period = new YearRange(2026, 2026);
        Organization department = unit(OrganizationType.DEPARTMENT);
        countedAwards(period, 3);
        when(consents.granted(USER_ID, ConsentType.ANALYTICS)).thenReturn(true);
        when(users.findById(USER_ID)).thenReturn(Optional.of(member(department)));
        when(statistics.standing(DEPARTMENT_ID, USER_ID, 3, period)).thenReturn(new DepartmentStanding(13, 2, 1));

        PersonalRank rank = service.of(USER_ID, 2026, 2026).rank();

        assertThat(rank.status()).isEqualTo(RankStatus.RANKED);
        assertThat(rank.position()).isEqualTo(3);
        assertThat(rank.groupSize()).isEqualTo(13);
        assertThat(rank.sharedWith()).isEqualTo(1);
        assertThat(rank.department().id()).isEqualTo(DEPARTMENT_ID);
        assertThat(rank.department().code()).isEqualTo("DAI");
    }

    @Test
    void ac1_7_withoutTheConsentNoFiguresAreRead() {
        YearRange period = new YearRange(2026, 2026);
        countedAwards(period, 3);
        withoutConsent();

        PersonalRank rank = service.of(USER_ID, 2026, 2026).rank();

        assertThat(rank).isEqualTo(PersonalRank.without(RankStatus.CONSENT_REQUIRED));
        verify(users, never()).findById(USER_ID);
    }

    @Test
    void ac1_7_aUnitThatIsNotADepartmentHasNoRank() {
        YearRange period = new YearRange(2026, 2026);
        countedAwards(period, 3);
        when(consents.granted(USER_ID, ConsentType.ANALYTICS)).thenReturn(true);
        when(users.findById(USER_ID)).thenReturn(Optional.of(member(unit(OrganizationType.FACULTY))));

        PersonalRank rank = service.of(USER_ID, 2026, 2026).rank();

        assertThat(rank).isEqualTo(PersonalRank.without(RankStatus.NO_DEPARTMENT));
    }

    @Test
    void ac1_7_anAccountThatIsNoLongerActiveHasNoRank() {
        YearRange period = new YearRange(2026, 2026);
        countedAwards(period, 3);
        User suspended = member(unit(OrganizationType.DEPARTMENT));
        suspended.setAccountStatus(AccountStatus.SUSPENDED);
        when(consents.granted(USER_ID, ConsentType.ANALYTICS)).thenReturn(true);
        when(users.findById(USER_ID)).thenReturn(Optional.of(suspended));

        assertThat(service.of(USER_ID, 2026, 2026).rank())
            .isEqualTo(PersonalRank.without(RankStatus.NO_DEPARTMENT));
        verify(statistics, never()).standing(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void ac1_7_aSmallGroupHasNoFigures() {
        YearRange period = new YearRange(2026, 2026);
        countedAwards(period, 3);
        when(consents.granted(USER_ID, ConsentType.ANALYTICS)).thenReturn(true);
        when(users.findById(USER_ID)).thenReturn(Optional.of(member(unit(OrganizationType.DEPARTMENT))));
        when(statistics.standing(DEPARTMENT_ID, USER_ID, 3, period))
            .thenReturn(new DepartmentStanding(MIN_GROUP_SIZE - 1, 0, 0));

        PersonalRank rank = service.of(USER_ID, 2026, 2026).rank();

        assertThat(rank.status()).isEqualTo(RankStatus.GROUP_TOO_SMALL);
        assertThat(rank.position()).isNull();
        assertThat(rank.groupSize()).isNull();
        assertThat(rank.sharedWith()).isNull();
        assertThat(rank.department().id()).isEqualTo(DEPARTMENT_ID);
    }

    private void assertInvalid(Integer from, Integer to, String parameter) {
        assertThatThrownBy(() -> service.of(USER_ID, from, to))
            .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(problem.getType()).isEqualTo("invalid-parameter");
                assertThat(problem.getProperties()).containsEntry("parameter", parameter);
            });
    }

    private void countedAwards(YearRange period, long count) {
        when(statistics.totals(USER_ID, period)).thenReturn(new CountedTotals(count, 0, null, null));
        when(statistics.openAwards(USER_ID)).thenReturn(new OpenAwards(0, 0));
    }

    private void withoutConsent() {
        when(consents.granted(USER_ID, ConsentType.ANALYTICS)).thenReturn(false);
    }

    private static Organization unit(OrganizationType type) {
        return Organization.builder().id(DEPARTMENT_ID).name("Department of Algebra and Informatics")
            .nameUk("Кафедра алгебри та інформатики").code("DAI").orgType(type).build();
    }

    private static User member(Organization organization) {
        return User.builder().id(USER_ID).organization(organization).accountStatus(AccountStatus.ACTIVE).build();
    }
}
