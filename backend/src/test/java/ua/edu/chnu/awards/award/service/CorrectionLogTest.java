package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.award.dto.FieldChange;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardCategory;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.award.event.AwardCorrected;
import ua.edu.chnu.awards.support.TestAwards;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.User;

class CorrectionLogTest {

    private final AuditService audit = mock(AuditService.class);
    private final ReviewerRule rule = mock(ReviewerRule.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CorrectionLog log = new CorrectionLog(audit, rule, events);
    private final User owner = TestUsers.person(21L, "owner@chnu.edu.ua", "Ірина",
        TestUsers.organization(TestUsers.DAI_DEPARTMENT_ID, OrganizationType.DEPARTMENT));

    @Test
    void ac3_3_theAuditRowCarriesOldAndNewValuesTheReasonAndTheDelegator() {
        AwardRequest request = TestAwards.request(award(category(13L, "Ministry", "Відзнака"))).build();
        when(rule.delegatorId(request)).thenReturn(77L);

        log.audit(request, 31L, List.of(new FieldChange("awardDate", LocalDate.of(2025, 5, 1), null)), "Дата");

        ArgumentCaptor<Map<String, Object>> details = captured();
        assertThat(details.getValue()).containsEntry("reason", "Дата").containsEntry("delegatorId", 77L)
            .containsEntry("requestId", TestAwards.REQUEST_ID).containsEntry("level", "FACULTY_SECRETARY")
            .containsEntry("changes", List.of(new FieldChange("awardDate", "2025-05-01", null)));
    }

    @Test
    void ac3_3_withoutADelegationTheAuditRowHasNoDelegator() {
        AwardRequest request = TestAwards.request(award(null)).build();
        when(rule.delegatorId(request)).thenReturn(null);

        log.audit(request, 31L, List.of(new FieldChange("titleUk", "А", "Б")), "Назва");

        ArgumentCaptor<Map<String, Object>> details = captured();
        assertThat(details.getValue()).doesNotContainKey("delegatorId");
    }

    @Test
    void ac3_5_theEventNamesCategoriesInBothLanguagesAndLeavesTheScoreOut() {
        AwardCategory before = category(21L, "University Award", "Університетська нагорода");
        Award award = award(category(13L, "Ministry", "Відзнака"));
        User reviewer = TestUsers.person(31L, "secretary@chnu.edu.ua", "Аліна", owner.getOrganization());

        log.announce(award, reviewer, "Категорія", List.of(new FieldChange("categoryId", 21L, 13L),
            new FieldChange("impactScore", 50, 80), new FieldChange("externalUrl", null, "https://mon.gov.ua")),
            before);

        ArgumentCaptor<AwardCorrected> event = ArgumentCaptor.forClass(AwardCorrected.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().email()).isEqualTo("owner@chnu.edu.ua");
        assertThat(event.getValue().titleUk()).isEqualTo("Letter");
        assertThat(event.getValue().changes()).containsExactly(
            new AwardCorrected.Change("categoryId", "University Award", "Ministry", "Університетська нагорода",
                "Відзнака"),
            new AwardCorrected.Change("externalUrl", null, "https://mon.gov.ua", null, "https://mon.gov.ua"));
    }

    @Test
    void theEventShowsTheAwardDateDayFirst() {
        Award award = award(null);

        log.announce(award, owner, "Дата", List.of(new FieldChange("awardDate", LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 2))), null);

        ArgumentCaptor<AwardCorrected> event = ArgumentCaptor.forClass(AwardCorrected.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().changes()).containsExactly(
            new AwardCorrected.Change("awardDate", "01.09.2026", "02.09.2026", "01.09.2026", "02.09.2026"));
    }

    @Test
    void ac3_5_aClearedCategoryHasNoName() {
        Award award = award(null);

        log.announce(award, owner, "Категорія", List.of(new FieldChange("categoryId", 21L, null)),
            category(21L, "University Award", "Університетська нагорода"));

        ArgumentCaptor<AwardCorrected> event = ArgumentCaptor.forClass(AwardCorrected.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().changes().getFirst().to()).isNull();
        assertThat(event.getValue().changes().getFirst().toUk()).isNull();
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> captured() {
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(audit).record(eq(AuditAction.AWARD_CORRECTED), eq(AuditEntityConstants.AWARDS), eq(31L),
            eq(TestAwards.AWARD_ID), details.capture());
        return details;
    }

    private Award award(AwardCategory category) {
        return TestAwards.award(owner, owner.getOrganization()).category(category).build();
    }

    private static AwardCategory category(long id, String name, String nameUk) {
        return AwardCategory.builder().id(id).name(name).nameUk(nameUk).level(RecognitionLevel.NATIONAL).build();
    }
}
