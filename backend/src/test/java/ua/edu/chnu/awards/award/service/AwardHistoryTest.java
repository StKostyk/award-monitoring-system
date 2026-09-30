package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.dto.AwardVersionResponse;
import ua.edu.chnu.awards.award.dto.FieldChange;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardSnapshot;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.AwardVersion;
import ua.edu.chnu.awards.award.entity.VersionAction;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.award.repository.AwardVersionRepository;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class AwardHistoryTest {

    private static final long OWNER_ID = 21L;
    private static final long DEAN_ID = 30L;
    private static final long AWARD_ID = 5L;
    private static final LocalDate MAY_FIRST = LocalDate.of(2025, 5, 1);

    private final AwardVersionRepository versions = mock(AwardVersionRepository.class);
    private final AwardRepository awards = mock(AwardRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AccessScope access = mock(AccessScope.class);
    private final AwardHistory history = new AwardHistory(versions, awards, users,
        new AwardOwnership(awards, users, access), access);
    private final Organization department = TestUsers.organization(64L, OrganizationType.DEPARTMENT);
    private final User owner = TestUsers.person(OWNER_ID, "owner@chnu.edu.ua", department);

    @BeforeEach
    void setUp() {
        when(access.callerId()).thenReturn(OWNER_ID);
        when(users.getReferenceById(anyLong())).thenAnswer(invocation ->
            TestUsers.person(invocation.getArgument(0), "actor@chnu.edu.ua", department));
    }

    @Test
    void ac1_1_theFirstVersionHasTheCallerAsActorAndNoChangedFields() {
        Award award = award(AwardStatus.DRAFT, 1L);
        when(versions.findFirstByAwardIdOrderByNumberDesc(AWARD_ID)).thenReturn(Optional.empty());

        history.created(award);

        AwardVersion saved = saved();
        assertThat(saved.getNumber()).isEqualTo(1L);
        assertThat(saved.getAction()).isEqualTo(VersionAction.CREATED);
        assertThat(saved.getActor().getId()).isEqualTo(OWNER_ID);
        assertThat(saved.getChangedFields()).isNull();
        assertThat(saved.getSnapshot().title()).isEqualTo("Letter");
        assertThat(saved.getSnapshot().organizationId()).isEqualTo(64L);
    }

    @Test
    void ac1_2_aChangedSaveRecordsTheNamesOfTheChangedFields() {
        Award award = award(AwardStatus.DRAFT, 2L);
        award.setTitle("Diploma");
        award.setAwardDate(MAY_FIRST);
        when(versions.findFirstByAwardIdOrderByNumberDesc(AWARD_ID))
            .thenReturn(Optional.of(version(1L, VersionAction.CREATED, snapshot("Letter", null, AwardStatus.DRAFT))));

        history.updated(award);

        assertThat(saved().getChangedFields()).containsExactly("title", "awardDate");
    }

    @Test
    void ac1_2_aSaveThatDidNotMoveTheVersionRecordsNothing() {
        Award award = award(AwardStatus.DRAFT, 3L);
        when(versions.findFirstByAwardIdOrderByNumberDesc(AWARD_ID))
            .thenReturn(Optional.of(version(3L, VersionAction.UPDATED, snapshot("Letter", null, AwardStatus.DRAFT))));

        history.updated(award);

        verify(versions, never()).save(any());
    }

    @Test
    void ac1_3_aSubmissionRecordsThePendingStateWithScoreAndOrganisation() {
        Award award = award(AwardStatus.PENDING, 4L);
        award.setImpactScore(80);
        when(versions.findFirstByAwardIdOrderByNumberDesc(AWARD_ID))
            .thenReturn(Optional.of(version(3L, VersionAction.UPDATED, snapshot("Letter", null, AwardStatus.DRAFT))));

        history.submitted(award);

        AwardVersion saved = saved();
        assertThat(saved.getAction()).isEqualTo(VersionAction.SUBMITTED);
        assertThat(saved.getSnapshot().status()).isEqualTo(AwardStatus.PENDING);
        assertThat(saved.getSnapshot().impactScore()).isEqualTo(80);
        assertThat(saved.getChangedFields()).containsExactly("status", "impactScore");
    }

    @Test
    void ac1_2_changesCompareEveryFieldIncludingEmptyValuesAndDates() {
        AwardSnapshot before = snapshot("Letter", null, AwardStatus.DRAFT);
        AwardSnapshot after = snapshot(null, MAY_FIRST, AwardStatus.PENDING);

        assertThat(AwardHistory.changes(before, after)).containsExactly(
            new FieldChange("title", "Letter", null),
            new FieldChange("awardDate", null, MAY_FIRST),
            new FieldChange("status", AwardStatus.DRAFT, AwardStatus.PENDING));
        assertThat(AwardHistory.changes(after, after)).isEmpty();
    }

    @Test
    void ac1_8_theOwnerReadsEveryVersionNewestFirstWithChangesAgainstThePreviousOne() {
        when(awards.findById(AWARD_ID)).thenReturn(Optional.of(award(AwardStatus.PENDING, 3L)));
        AwardVersion submitted = version(3L, VersionAction.SUBMITTED, snapshot("Diploma", MAY_FIRST,
            AwardStatus.PENDING));
        AwardVersion updated = version(2L, VersionAction.UPDATED, snapshot("Diploma", MAY_FIRST, AwardStatus.DRAFT));
        AwardVersion created = version(1L, VersionAction.CREATED, snapshot("Letter", null, AwardStatus.DRAFT));
        when(versions.findByAwardIdAndNumberGreaterThanEqual(eq(AWARD_ID), eq(Long.MIN_VALUE), any()))
            .thenReturn(page(List.of(submitted, updated, created)));

        Page<AwardVersionResponse> page = history.versions(AWARD_ID, 0, 20);

        assertThat(page.getContent()).extracting(AwardVersionResponse::number).containsExactly(3L, 2L, 1L);
        assertThat(page.getContent().get(0).changes()).extracting(FieldChange::field).containsExactly("status");
        assertThat(page.getContent().get(1).changes()).extracting(FieldChange::field)
            .containsExactly("title", "awardDate");
        assertThat(page.getContent().get(2).changes()).isEmpty();
        assertThat(page.getContent().get(2).actor().id()).isEqualTo(OWNER_ID);
    }

    @Test
    void ac1_8_theOldestVersionOfAPageIsComparedWithTheVersionBeforeIt() {
        when(awards.findById(AWARD_ID)).thenReturn(Optional.of(award(AwardStatus.DRAFT, 2L)));
        AwardVersion updated = version(2L, VersionAction.UPDATED, snapshot("Diploma", null, AwardStatus.DRAFT));
        when(versions.findByAwardIdAndNumberGreaterThanEqual(eq(AWARD_ID), eq(Long.MIN_VALUE), any()))
            .thenReturn(page(List.of(updated)));
        when(versions.findFirstByAwardIdAndNumberLessThanOrderByNumberDesc(AWARD_ID, 2L)).thenReturn(
            Optional.of(version(1L, VersionAction.CREATED, snapshot("Letter", null, AwardStatus.DRAFT))));

        Page<AwardVersionResponse> page = history.versions(AWARD_ID, 1, 1);

        assertThat(page.getContent()).singleElement().satisfies(version ->
            assertThat(version.changes()).containsExactly(new FieldChange("title", "Letter", "Diploma")));
    }

    @Test
    void ac1_8_thePageSizeIsCappedAt50() {
        when(awards.findById(AWARD_ID)).thenReturn(Optional.of(award(AwardStatus.DRAFT, 1L)));
        when(versions.findByAwardIdAndNumberGreaterThanEqual(eq(AWARD_ID), anyLong(), any()))
            .thenReturn(page(List.of()));

        history.versions(AWARD_ID, 0, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(versions).findByAwardIdAndNumberGreaterThanEqual(eq(AWARD_ID), anyLong(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(AwardHistory.MAX_SIZE);
    }

    @Test
    void ac1_9_aReaderInScopeSeesTheAwardFromItsSubmissionWithoutChangesOnIt() {
        when(access.callerId()).thenReturn(DEAN_ID);
        when(access.canReadAwards(64L)).thenReturn(true);
        when(awards.findById(AWARD_ID)).thenReturn(Optional.of(award(AwardStatus.PENDING, 3L)));
        when(versions.firstNumber(AWARD_ID, VersionAction.SUBMITTED)).thenReturn(Optional.of(3L));
        AwardVersion submitted = version(3L, VersionAction.SUBMITTED, snapshot("Diploma", MAY_FIRST,
            AwardStatus.PENDING));
        when(versions.findByAwardIdAndNumberGreaterThanEqual(eq(AWARD_ID), eq(3L), any()))
            .thenReturn(page(List.of(submitted)));

        Page<AwardVersionResponse> page = history.versions(AWARD_ID, 0, 20);

        assertThat(page.getContent()).singleElement().satisfies(version -> {
            assertThat(version.action()).isEqualTo(VersionAction.SUBMITTED);
            assertThat(version.changes()).isEmpty();
        });
        verify(versions, never()).findFirstByAwardIdAndNumberLessThanOrderByNumberDesc(anyLong(), anyLong());
    }

    @Test
    void ac1_9_anAwardSubmittedBeforeVersioningIsReadFromItsBaseline() {
        when(access.callerId()).thenReturn(DEAN_ID);
        when(access.canReadAwards(64L)).thenReturn(true);
        when(awards.findById(AWARD_ID)).thenReturn(Optional.of(award(AwardStatus.PENDING, 7L)));
        when(versions.firstNumber(AWARD_ID, VersionAction.SUBMITTED)).thenReturn(Optional.empty());
        when(versions.firstNumber(AWARD_ID, VersionAction.BASELINE)).thenReturn(Optional.of(7L));
        when(versions.findByAwardIdAndNumberGreaterThanEqual(eq(AWARD_ID), eq(7L), any()))
            .thenReturn(page(List.of(version(7L, VersionAction.BASELINE, snapshot("Letter", MAY_FIRST,
                AwardStatus.PENDING)))));

        assertThat(history.versions(AWARD_ID, 0, 20).getContent()).singleElement()
            .satisfies(version -> assertThat(version.actor()).isNull());
    }

    @Test
    void ac1_9_aDraftOrAnAwardOutOfScopeIsNotFoundForOthers() {
        when(access.callerId()).thenReturn(DEAN_ID);
        when(access.canReadAwards(64L)).thenReturn(true);
        when(awards.findById(AWARD_ID)).thenReturn(Optional.of(award(AwardStatus.DRAFT, 1L)));

        assertThatThrownBy(() -> history.versions(AWARD_ID, 0, 20)).isInstanceOf(AwardNotFoundException.class);

        when(awards.findById(AWARD_ID)).thenReturn(Optional.of(award(AwardStatus.PENDING, 3L)));
        when(access.canReadAwards(64L)).thenReturn(false);
        assertThatThrownBy(() -> history.versions(AWARD_ID, 0, 20)).isInstanceOf(AwardNotFoundException.class);
        assertThatThrownBy(() -> history.versions(6L, 0, 20)).isInstanceOf(AwardNotFoundException.class);
    }

    private AwardVersion saved() {
        ArgumentCaptor<AwardVersion> captor = ArgumentCaptor.forClass(AwardVersion.class);
        verify(versions).save(captor.capture());
        return captor.getValue();
    }

    private Award award(AwardStatus status, long version) {
        return Award.builder().id(AWARD_ID).owner(owner).organization(department).title("Letter").status(status)
            .version(version).build();
    }

    private AwardVersion version(long number, VersionAction action, AwardSnapshot snapshot) {
        return AwardVersion.builder().awardId(AWARD_ID).number(number).action(action)
            .actor(action == VersionAction.BASELINE ? null : owner).snapshot(snapshot).build();
    }

    private static AwardSnapshot snapshot(String title, LocalDate date, AwardStatus status) {
        return new AwardSnapshot(title, null, null, null, null, date, null, status, null, false, null, 64L);
    }

    private static Page<AwardVersion> page(List<AwardVersion> rows) {
        return new PageImpl<>(rows);
    }
}
