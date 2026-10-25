package ua.edu.chnu.awards.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ua.edu.chnu.awards.analytics.config.AnalyticsProperties;
import ua.edu.chnu.awards.analytics.dto.TopPerformers;
import ua.edu.chnu.awards.analytics.dto.UnitFigures;
import ua.edu.chnu.awards.award.dto.OwnerAwardCount;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.gdpr.ConsentLookup;
import ua.edu.chnu.awards.gdpr.dto.ConsentType;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;

@ExtendWith(MockitoExtension.class)
class TopPerformersRankingTest {

    private static final int MIN_GROUP_SIZE = 5;
    private static final int TOP_PERFORMERS = 3;
    private static final long DAI = 64L;
    private static final long DMA = 66L;

    @Mock
    private ConsentLookup consents;
    @Mock
    private UserAccounts users;

    private TopPerformersRanking ranking;

    @BeforeEach
    void setUp() {
        ranking = new TopPerformersRanking(consents, users, new AnalyticsProperties(MIN_GROUP_SIZE, 500,
            TOP_PERFORMERS, Duration.ofMinutes(5), Duration.ofDays(2)));
    }

    @Test
    void ac1_7_belowTheMinimumGroupNobodyIsListed() {
        TopPerformers top = ranking.of(figures(List.of(owner(1, DAI, true, 4, RecognitionLevel.NATIONAL))),
            MIN_GROUP_SIZE - 1);

        assertThat(top.status()).isEqualTo(TopPerformers.Status.GROUP_TOO_SMALL);
        assertThat(top.people()).isEmpty();
        verifyNoInteractions(consents, users);
    }

    @Test
    void ac1_7_onlyActiveConsentingOwnersAreListedByCountThenSurnameUpToTheLimit() {
        List<OwnerAwardCount> owners = new ArrayList<>(List.of(
            owner(1, DAI, true, 2, RecognitionLevel.FACULTY), owner(1, DMA, true, 1, RecognitionLevel.NATIONAL),
            owner(1, DAI, false, 9, RecognitionLevel.INTERNATIONAL),
            owner(2, DAI, true, 3, RecognitionLevel.REGIONAL), owner(3, DAI, true, 3, RecognitionLevel.LOCAL),
            owner(4, DAI, true, 9, RecognitionLevel.NATIONAL), owner(5, DAI, true, 8, RecognitionLevel.NATIONAL),
            owner(6, DAI, true, 1, RecognitionLevel.LOCAL)));
        when(consents.grantedAmong(any(), any())).thenAnswer(call -> {
            Collection<Long> asked = call.getArgument(0);
            assertThat(asked).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L);
            assertThat(call.<ConsentType>getArgument(1)).isEqualTo(ConsentType.ANALYTICS);
            return Set.of(1L, 2L, 3L, 5L, 6L);
        });
        when(users.findAllById(any())).thenAnswer(call -> {
            List<User> found = new ArrayList<>();
            call.<Iterable<Long>>getArgument(0).forEach(id -> found.add(user(id)));
            return found;
        });

        TopPerformers top = ranking.of(figures(owners), MIN_GROUP_SIZE);

        assertThat(top.status()).isEqualTo(TopPerformers.Status.LISTED);
        assertThat(top.people()).extracting(TopPerformers.Person::userId, TopPerformers.Person::lastName,
                TopPerformers.Person::count, TopPerformers.Person::highestLevel)
            .containsExactly(tuple(2L, "Бондар", 3L, RecognitionLevel.REGIONAL),
                tuple(1L, "Ковальчук", 3L, RecognitionLevel.NATIONAL),
                tuple(3L, "Ткачук", 3L, RecognitionLevel.LOCAL));
        assertThat(top.people().getFirst().department().code()).isEqualTo("DAI");
        assertThat(top.people().getFirst().firstName()).isEqualTo("Ім'я 2");
    }

    @Test
    void ac1_7_withoutConsentingOwnersTheListIsEmpty() {
        when(consents.grantedAmong(any(), any())).thenReturn(Set.of());
        when(users.findAllById(any())).thenReturn(List.of());

        TopPerformers top = ranking.of(figures(LongStream.rangeClosed(1, MIN_GROUP_SIZE)
            .mapToObj(id -> owner(id, DAI, true, 1, RecognitionLevel.LOCAL)).toList()), MIN_GROUP_SIZE);

        assertThat(top.status()).isEqualTo(TopPerformers.Status.LISTED);
        assertThat(top.people()).isEmpty();
    }

    private static User user(long id) {
        String lastName = switch ((int) id) {
            case 1 -> "Ковальчук";
            case 2 -> "Бондар";
            case 3 -> "Ткачук";
            default -> "Лисюк";
        };
        Organization department = Organization.builder().id(DAI).name("Algebra and Informatics")
            .nameUk("Кафедра алгебри та інформатики").code("DAI").orgType(OrganizationType.DEPARTMENT).build();
        return User.builder().id(id).firstName("Ім'я " + id).lastName(lastName).organization(department)
            .accountStatus(id == 5 ? AccountStatus.INACTIVE : AccountStatus.ACTIVE).build();
    }

    private static OwnerAwardCount owner(long userId, long organizationId, boolean current, long count,
                                         RecognitionLevel level) {
        return new OwnerAwardCount(userId, organizationId, current, count, level);
    }

    private static UnitFigures figures(List<OwnerAwardCount> owners) {
        return new UnitFigures(Instant.parse("2026-10-19T09:58:00Z"), List.of(), owners, List.of(), List.of(),
            List.of());
    }
}
