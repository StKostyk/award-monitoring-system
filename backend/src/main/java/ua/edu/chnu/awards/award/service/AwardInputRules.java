package ua.edu.chnu.awards.award.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.dto.AwardForm;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardCategory;
import ua.edu.chnu.awards.award.repository.AwardCategoryRepository;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;

import lombok.RequiredArgsConstructor;

/**
 * Field rules of the award form: what a draft may hold and what a submission needs. The date rules are those of
 * {@link AwardDateRules}.
 */
@Component
@RequiredArgsConstructor
public class AwardInputRules {

    static final int TITLE_MAX = 500;
    static final int DESCRIPTION_MAX = 4000;
    static final int ORGANIZATION_MAX = 255;
    static final int URL_MAX = 2048;
    static final String CATEGORY = "categoryId";
    static final String AWARD_DATE = "awardDate";
    private static final Set<String> WEB_SCHEMES = Set.of("http", "https");

    private final AwardCategoryRepository categories;
    private final AwardDateRules dates;

    /**
     * The form with surrounding spaces removed and blank texts treated as empty.
     *
     * @param form the form as sent
     * @return the cleaned form
     */
    public AwardForm normalize(AwardForm form) {
        return new AwardForm(clean(form.title()), clean(form.titleUk()), clean(form.description()),
            clean(form.descriptionUk()), form.categoryId(), clean(form.awardingOrganization()), form.awardDate(),
            clean(form.externalUrl()), form.version());
    }

    /**
     * Checks the fields of a draft; fields that are empty are not checked beyond the title.
     *
     * @param form    the cleaned form
     * @param current the category the draft already has, which may stay even if it was deactivated since
     * @return the chosen category, empty when none was chosen
     * @throws ApiProblemException 422 {@code validation-failed} listing every refused field
     */
    public Optional<AwardCategory> check(AwardForm form, Optional<AwardCategory> current) {
        List<FieldViolation> errors = textErrors(form);
        dates.check(form.awardDate()).ifPresent(errors::add);
        Optional<AwardCategory> category = Optional.ofNullable(form.categoryId())
            .flatMap(categories::findById)
            .filter(found -> found.isActive() || current.map(AwardCategory::getId).filter(found.getId()::equals)
                .isPresent());
        if (form.categoryId() != null && category.isEmpty()) {
            errors.add(inactiveCategory());
        }
        if (!errors.isEmpty()) {
            throw refused("validation-failed", "The award form has invalid fields", errors);
        }
        return category;
    }

    private static List<FieldViolation> textErrors(AwardForm form) {
        List<FieldViolation> errors = new ArrayList<>();
        if (form.title() == null && form.titleUk() == null) {
            errors.add(new FieldViolation("title", "required", "A title in Ukrainian or English is required"));
        }
        tooLong(errors, "title", form.title(), TITLE_MAX);
        tooLong(errors, "titleUk", form.titleUk(), TITLE_MAX);
        tooLong(errors, "description", form.description(), DESCRIPTION_MAX);
        tooLong(errors, "descriptionUk", form.descriptionUk(), DESCRIPTION_MAX);
        tooLong(errors, "awardingOrganization", form.awardingOrganization(), ORGANIZATION_MAX);
        if (form.externalUrl() != null && !tooLong(errors, "externalUrl", form.externalUrl(), URL_MAX)
            && !isWebLink(form.externalUrl())) {
            errors.add(new FieldViolation("externalUrl", "invalid", "The link must be an http or https address"));
        }
        return errors;
    }

    /**
     * Checks that a draft can be submitted: the fields a request needs are filled, the category is still
     * offered and the date is acceptable.
     *
     * @param award the draft
     * @throws ApiProblemException 422 {@code award-incomplete} naming the empty fields, or
     *                             {@code validation-failed} for a deactivated category or a refused date
     */
    public void checkComplete(Award award) {
        List<FieldViolation> missing = new ArrayList<>();
        if (award.getCategory() == null) {
            missing.add(required(CATEGORY));
        }
        if (award.getAwardingOrganization() == null) {
            missing.add(required("awardingOrganization"));
        }
        if (award.getAwardDate() == null) {
            missing.add(required(AWARD_DATE));
        }
        if (!missing.isEmpty()) {
            throw refused("award-incomplete", "The award is missing fields a submission needs", missing);
        }
        List<FieldViolation> errors = new ArrayList<>();
        if (!award.getCategory().isActive()) {
            errors.add(inactiveCategory());
        }
        dates.check(award.getAwardDate()).ifPresent(errors::add);
        if (!errors.isEmpty()) {
            throw refused("validation-failed", "The award has invalid fields", errors);
        }
    }

    private static boolean tooLong(List<FieldViolation> errors, String field, String value, int max) {
        boolean tooLong = value != null && value.length() > max;
        if (tooLong) {
            errors.add(new FieldViolation(field, "too-long", "At most " + max + " characters"));
        }
        return tooLong;
    }

    private static boolean isWebLink(String value) {
        try {
            URI uri = new URI(value);
            return uri.getScheme() != null && WEB_SCHEMES.contains(uri.getScheme().toLowerCase(Locale.ROOT))
                && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static FieldViolation required(String field) {
        return new FieldViolation(field, "required", "Required for a submission");
    }

    private static FieldViolation inactiveCategory() {
        return new FieldViolation(CATEGORY, "inactive", "The category is no longer available");
    }

    private static ApiProblemException refused(String type, String detail, List<FieldViolation> errors) {
        return new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, type, detail, Map.of("errors", errors));
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
