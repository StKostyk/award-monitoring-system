package ua.edu.chnu.awards.award.service;

import java.util.List;
import java.util.Locale;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.award.dto.ReviewDecisionRequest.Decision;
import ua.edu.chnu.awards.award.dto.ReviewTemplateView;
import ua.edu.chnu.awards.award.entity.ReviewTemplate;
import ua.edu.chnu.awards.award.repository.ReviewTemplateRepository;

import lombok.RequiredArgsConstructor;

/**
 * The comment templates a reviewer picks from, in the language of the request with Ukrainian as fallback.
 */
@Service
@RequiredArgsConstructor
public class ReviewTemplates {

    private final ReviewTemplateRepository templates;

    /**
     * Lists the active templates of a decision.
     *
     * @param decision the decision
     * @return the templates in list order, title and body in the caller's language
     */
    @Transactional(readOnly = true)
    public List<ReviewTemplateView> of(Decision decision) {
        boolean english = Locale.ENGLISH.getLanguage().equals(LocaleContextHolder.getLocale().getLanguage());
        return templates.findByDecisionAndActiveTrueOrderBySortOrderAscIdAsc(decision).stream()
            .map(template -> view(template, english))
            .toList();
    }

    private static ReviewTemplateView view(ReviewTemplate template, boolean english) {
        boolean translated = english && template.getTitleEn() != null;
        return new ReviewTemplateView(template.getId(), template.getDecision(),
            translated ? template.getTitleEn() : template.getTitleUk(),
            translated ? template.getBodyEn() : template.getBodyUk());
    }
}
