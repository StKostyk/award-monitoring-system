package ua.edu.chnu.awards.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.event.AwardCorrected;
import ua.edu.chnu.awards.award.event.AwardDecided;
import ua.edu.chnu.awards.award.event.OverdueNoticed;
import ua.edu.chnu.awards.award.event.ReviewTakenOver;
import ua.edu.chnu.awards.notification.entity.NotificationType;

class NotificationDraftsTest {

    private static final Instant AT = Instant.parse("2026-10-14T10:00:00Z");
    private static final String EMAIL = "olena@chnu.edu.ua";

    private final NotificationDrafts drafts = new NotificationDrafts();

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, names = {"APPROVED", "REJECTED", "RETURNED", "ESCALATED"})
    void ac1_2_aDecisionBecomesTheTypeOfItsOutcomeWithoutTheComment(RequestStatus outcome) {
        NotificationDraft draft = drafts.of(new AwardDecided(7L, EMAIL, "Олена", 5L, "Certificate", "Грамота",
            outcome, ApprovalLevel.DEAN, "Ірина Бойко", "Секретний коментар", AT));

        assertThat(draft.type()).isEqualTo(NotificationType.valueOf("AWARD_" + outcome.name()));
        assertThat(draft.userId()).isEqualTo(7L);
        assertThat(draft.referenceType()).isEqualTo("AWARD");
        assertThat(draft.referenceId()).isEqualTo(5L);
        assertThat(draft.occurredAt()).isEqualTo(AT);
        assertThat(draft.payload()).containsOnlyKeys("title", "titleUk", "reviewer", "level")
            .containsEntry("level", "DEAN").doesNotContainValue("Секретний коментар");
    }

    @Test
    void ac1_2_aCorrectionNamesTheFieldsButNotTheirValues() {
        NotificationDraft draft = drafts.of(new AwardCorrected(7L, EMAIL, "Олена", 5L, "Certificate", "Грамота",
            "Ірина Бойко", "Typo", List.of(new AwardCorrected.Change("awardDate", "2025-05-01", "2025-06-01",
                "2025-05-01", "2025-06-01")), AT));

        assertThat(draft.type()).isEqualTo(NotificationType.AWARD_CORRECTED);
        assertThat(draft.payload()).containsOnlyKeys("title", "titleUk", "reviewer", "fields")
            .containsEntry("fields", List.of("awardDate"));
        assertThat(draft.payload().toString()).doesNotContain("2025-06-01", "Typo");
    }

    @Test
    void ac1_2_anOverdueDigestCountsItsRequestsAndNamesTheOldestDeadline() {
        Instant oldest = Instant.parse("2026-10-06T09:00:00Z");
        NotificationDraft draft = drafts.of(new OverdueNoticed(3L, EMAIL, "Ірина Бойко", List.of(
            item(oldest.plusSeconds(3600)), item(oldest)), AT));

        assertThat(draft.type()).isEqualTo(NotificationType.REVIEW_OVERDUE);
        assertThat(draft.referenceType()).isNull();
        assertThat(draft.referenceId()).isNull();
        assertThat(draft.payload()).containsEntry("count", 2).containsEntry("oldestDeadline", oldest.toString());
    }

    @Test
    void ac1_2_aTakeoverPointsAtTheAward() {
        NotificationDraft draft = drafts.of(new ReviewTakenOver(3L, EMAIL, "Ірина", 5L, "Certificate", "Грамота",
            "Петро Іваненко", AT));

        assertThat(draft.type()).isEqualTo(NotificationType.REVIEW_TAKEN_OVER);
        assertThat(draft.referenceId()).isEqualTo(5L);
        assertThat(draft.payload()).containsEntry("reviewer", "Петро Іваненко");
    }

    private static OverdueNoticed.Item item(Instant deadline) {
        return new OverdueNoticed.Item(5L, "Certificate", "Грамота", "Анастасія Коваль",
            ApprovalLevel.FACULTY_SECRETARY, deadline, null);
    }
}
