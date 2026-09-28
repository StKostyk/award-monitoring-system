package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.award.dto.AwardForm;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardCategory;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.award.repository.AwardCategoryRepository;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;

class AwardInputRulesTest {

    private static final LocalDate KYIV_TODAY = LocalDate.of(2026, 9, 28);
    private static final String TITLE = "Грамота Міністерства освіти і науки";

    private final AwardCategoryRepository categories = mock(AwardCategoryRepository.class);
    private final Clock justAfterKyivMidnight = Clock.fixed(Instant.parse("2026-09-27T21:30:00Z"),
        ZoneId.of("Europe/Kyiv"));
    private final AwardInputRules rules = new AwardInputRules(categories, justAfterKyivMidnight);
    private final AwardCategory active = category(13L, true);
    private final AwardCategory retired = category(14L, false);

    @BeforeEach
    void setUp() {
        when(categories.findById(13L)).thenReturn(Optional.of(active));
        when(categories.findById(14L)).thenReturn(Optional.of(retired));
        when(categories.findById(999L)).thenReturn(Optional.empty());
    }

    @Test
    void ac1_1_normalizeTrimsTextsAndTreatsBlankAsEmpty() {
        AwardForm clean = rules.normalize(new AwardForm("  Letter  ", " ", null, "\t", 13L, " MON ", null,
            " https://mon.gov.ua ", 3L));

        assertThat(clean).isEqualTo(new AwardForm("Letter", null, null, null, 13L, "MON", null,
            "https://mon.gov.ua", 3L));
    }

    @Test
    void ac1_1_aDraftWithOnlyAUkrainianTitleIsAccepted() {
        assertThat(rules.check(form(null, TITLE, null, null, null), Optional.empty())).isEmpty();
    }

    @Test
    void ac1_1_aDraftNeedsATitleInOneLanguage() {
        assertThatThrownBy(() -> rules.check(form(null, null, null, null, null), Optional.empty()))
            .satisfies(e -> assertProblem(e, "validation-failed"))
            .extracting(e -> ((ApiProblemException) e).getProperties().get("errors"), list(FieldViolation.class))
            .extracting(FieldViolation::field, FieldViolation::code)
            .containsExactly(tuple("title", "required"));
    }

    @Test
    void ac1_1_everyRefusedFieldIsListed() {
        AwardForm form = new AwardForm("x".repeat(501), "т".repeat(501), "d".repeat(4001), "о".repeat(4001),
            999L, "o".repeat(256), KYIV_TODAY.plusDays(1), "javascript:alert(1)", null);

        assertThatThrownBy(() -> rules.check(form, Optional.empty()))
            .extracting(e -> ((ApiProblemException) e).getProperties().get("errors"), list(FieldViolation.class))
            .extracting(FieldViolation::field)
            .containsExactlyInAnyOrder("title", "titleUk", "description", "descriptionUk", "awardingOrganization",
                "externalUrl", "awardDate", "categoryId");
    }

    @Test
    void ac1_1_limitsAreInclusive() {
        AwardForm form = new AwardForm("x".repeat(500), null, "d".repeat(4000), null, 13L, "o".repeat(255),
            KYIV_TODAY, "https://example.org/" + "p".repeat(2028), null);

        assertThat(rules.check(form, Optional.empty())).contains(active);
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "ftp://example.org/x", "mon.gov.ua", "https://", "http://a b"})
    void ac1_1_onlyHttpAndHttpsLinksAreAccepted(String link) {
        assertThatThrownBy(() -> rules.check(form(TITLE, null, null, null, link), Optional.empty()))
            .satisfies(e -> assertProblem(e, "validation-failed"));
    }

    @Test
    void ac1_1_aTooLongLinkIsReportedOnce() {
        AwardForm form = form(TITLE, null, null, null, "https://example.org/" + "p".repeat(2030));

        assertThatThrownBy(() -> rules.check(form, Optional.empty()))
            .extracting(e -> ((ApiProblemException) e).getProperties().get("errors"), list(FieldViolation.class))
            .extracting(FieldViolation::code).containsExactly("too-long");
    }

    @Test
    void ac2_1_todayIsTheKyivDayEvenWhileUtcIsStillYesterday() {
        assertThat(rules.check(form(TITLE, null, null, KYIV_TODAY, null), Optional.empty())).isEmpty();
        assertThatThrownBy(() -> rules.check(form(TITLE, null, null, KYIV_TODAY.plusDays(1), null),
            Optional.empty()))
            .extracting(e -> ((ApiProblemException) e).getProperties().get("errors"), list(FieldViolation.class))
            .extracting(FieldViolation::field, FieldViolation::code)
            .containsExactly(tuple("awardDate", "future"));
    }

    @Test
    void edge_aDeactivatedCategoryMayStayOnTheDraftThatHasItButCannotBeChosen() {
        assertThat(rules.check(form(TITLE, null, 14L, null, null), Optional.of(retired))).contains(retired);
        assertThatThrownBy(() -> rules.check(form(TITLE, null, 14L, null, null), Optional.of(active)))
            .satisfies(e -> assertProblem(e, "validation-failed"));
    }

    @Test
    void ac1_5_submissionNamesEveryMissingField() {
        Award draft = Award.builder().title(TITLE).build();

        assertThatThrownBy(() -> rules.checkComplete(draft))
            .satisfies(e -> assertProblem(e, "award-incomplete"))
            .extracting(e -> ((ApiProblemException) e).getProperties().get("errors"), list(FieldViolation.class))
            .extracting(FieldViolation::field)
            .containsExactly("categoryId", "awardingOrganization", "awardDate");
    }

    @Test
    void edge_submissionRefusesACategoryDeactivatedSinceTheDraftWasSaved() {
        Award draft = Award.builder().title(TITLE).category(retired).awardingOrganization("MON")
            .awardDate(KYIV_TODAY.plusDays(1)).build();

        assertThatThrownBy(() -> rules.checkComplete(draft))
            .satisfies(e -> assertProblem(e, "validation-failed"))
            .extracting(e -> ((ApiProblemException) e).getProperties().get("errors"), list(FieldViolation.class))
            .extracting(FieldViolation::field)
            .containsExactly("categoryId", "awardDate");
    }

    @Test
    void ac1_5_aCompleteDraftPasses() {
        Award draft = Award.builder().title(TITLE).category(active).awardingOrganization("MON")
            .awardDate(KYIV_TODAY).build();

        rules.checkComplete(draft);

        assertThat(draft.getStatus()).isNotNull();
    }

    private static void assertProblem(Throwable error, String type) {
        assertThat(error).isInstanceOf(ApiProblemException.class);
        assertThat(((ApiProblemException) error).getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(((ApiProblemException) error).getType()).isEqualTo(type);
    }

    private static AwardForm form(String title, String titleUk, Long categoryId, LocalDate date, String link) {
        return new AwardForm(title, titleUk, null, null, categoryId, null, date, link, null);
    }

    private static AwardCategory category(long id, boolean isActive) {
        return AwardCategory.builder().id(id).name("Ministry").level(RecognitionLevel.NATIONAL).active(isActive)
            .build();
    }
}
