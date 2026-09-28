package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.support.TestUsers;

class AwardOwnershipTest {

    private static final long OWNER_ID = 21L;

    private final AwardRepository awards = mock(AwardRepository.class);
    private final AccessScope access = mock(AccessScope.class);
    private final AwardOwnership ownership = new AwardOwnership(awards, access);

    @BeforeEach
    void setUp() {
        when(access.callerId()).thenReturn(OWNER_ID);
    }

    @Test
    void ac1_3_theOwnersDraftIsReturnedLocked() {
        Award draft = award(OWNER_ID, AwardStatus.DRAFT);
        when(awards.findForUpdate(5L)).thenReturn(Optional.of(draft));

        assertThat(ownership.lockedDraft(5L)).isSameAs(draft);
    }

    @Test
    void ac1_8_somebodyElsesAwardIsUnknown() {
        when(awards.findForUpdate(5L)).thenReturn(Optional.of(award(99L, AwardStatus.DRAFT)));

        assertThatThrownBy(() -> ownership.lockedDraft(5L)).isInstanceOf(AwardNotFoundException.class);
        assertThatThrownBy(() -> ownership.lockedDraft(6L)).isInstanceOf(AwardNotFoundException.class);
    }

    @Test
    void ac1_4_aSubmittedAwardIsNotEditable() {
        when(awards.findForUpdate(5L)).thenReturn(Optional.of(award(OWNER_ID, AwardStatus.PENDING)));

        assertThatThrownBy(() -> ownership.lockedDraft(5L))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(e.getType()).isEqualTo("award-not-editable");
                assertThat(e.getProperties()).containsEntry("awardStatus", "PENDING");
            });
    }

    @Test
    void ac1_3_aStaleVersionAnswersWithTheCurrentOne() {
        Award draft = award(OWNER_ID, AwardStatus.DRAFT);

        ownership.requireVersion(draft, 4L);
        assertThatThrownBy(() -> ownership.requireVersion(draft, 3L))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(e.getType()).isEqualTo("award-stale");
                assertThat(e.getProperties()).containsEntry("currentVersion", 4L);
            });
        assertThatThrownBy(() -> ownership.requireVersion(draft, null))
            .isInstanceOfSatisfying(ApiProblemException.class,
                e -> assertThat(e.getType()).isEqualTo("validation-failed"));
    }

    private static Award award(long ownerId, AwardStatus status) {
        return Award.builder().id(5L).owner(TestUsers.person(ownerId, "owner@chnu.edu.ua")).status(status)
            .version(4L).build();
    }
}
