package ua.edu.chnu.awards.award.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.award.dto.ReturnedReviewItem;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.entity.ReviewDecisionType;
import ua.edu.chnu.awards.award.repository.ReviewDecisionRepository;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.entity.OrganizationType;
import ua.edu.chnu.awards.support.TestAwards;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.User;

class ReturnedItemMapperTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    private final ReviewDecisionRepository decisions = mock(ReviewDecisionRepository.class);
    private final ReturnedItemMapper mapper = new ReturnedItemMapper(decisions, Clock.fixed(NOW, ZoneOffset.UTC));
    private final Organization department = TestUsers.organization(64L, OrganizationType.DEPARTMENT);
    private final User owner = TestUsers.person(21L, "owner@chnu.edu.ua", department);
    private final User secretary = TestUsers.person(22L, "secretary@chnu.edu.ua", department);
    private final User colleague = TestUsers.person(23L, "colleague@chnu.edu.ua", department);

    @Test
    void ac1_theLatestReturnGivesWhoWhenWhyAndTheDaysWaiting() {
        AwardRequest request = returned();
        when(decisions.findLatestFirst(anyCollection(), eq(ReviewDecisionType.RETURNED))).thenReturn(List.of(
            TestAwards.decision(2L, ReviewDecisionType.RETURNED, ApprovalLevel.FACULTY_SECRETARY, colleague,
                Instant.parse("2026-11-17T09:00:00Z")),
            TestAwards.decision(1L, ReviewDecisionType.RETURNED, ApprovalLevel.FACULTY_SECRETARY, secretary,
                Instant.parse("2026-11-10T09:00:00Z"))));

        ReturnedReviewItem item = mapper.toItems(List.of(request)).getFirst();

        assertThat(item.awardId()).isEqualTo(TestAwards.AWARD_ID);
        assertThat(item.returnedBy().id()).isEqualTo(23L);
        assertThat(item.returnedFor()).isNull();
        assertThat(item.comment()).isEqualTo("comment 2");
        assertThat(item.returnedAt()).isEqualTo(Instant.parse("2026-11-17T09:00:00Z"));
        assertThat(item.daysWaiting()).isEqualTo(3);
        assertThat(item.level()).isEqualTo(ApprovalLevel.FACULTY_SECRETARY);
    }

    @Test
    void ac1_aRequestWithoutAReturnRowStillListsTheAward() {
        when(decisions.findLatestFirst(anyCollection(), eq(ReviewDecisionType.RETURNED))).thenReturn(List.of());

        ReturnedReviewItem item = mapper.toItems(List.of(returned())).getFirst();

        assertThat(item.returnedBy()).isNull();
        assertThat(item.returnedAt()).isNull();
        assertThat(item.daysWaiting()).isZero();
        assertThat(item.level()).isEqualTo(ApprovalLevel.FACULTY_SECRETARY);
    }

    @Test
    void anEmptyPageReadsNothing() {
        assertThat(mapper.toItems(List.of())).isEmpty();
        verifyNoInteractions(decisions);
    }

    private AwardRequest returned() {
        Award award = TestAwards.award(owner, department).build();
        return TestAwards.request(award).status(RequestStatus.RETURNED).build();
    }
}
