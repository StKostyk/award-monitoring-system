package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import ua.edu.chnu.awards.award.dto.ReviewDecisionRequest.Decision;
import ua.edu.chnu.awards.award.dto.ReviewTemplateView;
import ua.edu.chnu.awards.award.entity.ReviewTemplate;
import ua.edu.chnu.awards.award.repository.ReviewTemplateRepository;

class ReviewTemplatesTest {

    private static final Locale UKRAINIAN = Locale.forLanguageTag("uk");

    private final ReviewTemplateRepository repository = mock(ReviewTemplateRepository.class);
    private final ReviewTemplates templates = new ReviewTemplates(repository);

    @BeforeEach
    void seed() {
        when(repository.findByDecisionAndActiveTrueOrderBySortOrderAscIdAsc(Decision.RETURN)).thenReturn(List.of(
            ReviewTemplate.builder().id(1L).decision(Decision.RETURN).titleUk("Немає скану").titleEn("No scan")
                .bodyUk("Додайте скан").bodyEn("Attach a scan").active(true).build(),
            ReviewTemplate.builder().id(2L).decision(Decision.RETURN).titleUk("Невірна категорія")
                .bodyUk("Оберіть категорію").active(true).build()));
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void ac4_5_templatesAreInUkrainianByDefault() {
        LocaleContextHolder.setLocale(UKRAINIAN);

        assertThat(templates.of(Decision.RETURN)).extracting(ReviewTemplateView::title)
            .containsExactly("Немає скану", "Невірна категорія");
    }

    @Test
    void ac4_5_englishCallersGetEnglishWithUkrainianAsFallback() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        List<ReviewTemplateView> list = templates.of(Decision.RETURN);

        assertThat(list).extracting(ReviewTemplateView::title).containsExactly("No scan", "Невірна категорія");
        assertThat(list).extracting(ReviewTemplateView::body).containsExactly("Attach a scan", "Оберіть категорію");
        assertThat(list.getFirst().id()).isEqualTo(1L);
        assertThat(list.getFirst().decision()).isEqualTo(Decision.RETURN);
    }
}
