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
    static final String TITLE = "title";
    static final String CATEGORY = "categoryId";
    static final String RECIPIENT = "recipientOrganizationId";
    private static final Set<String> WEB_SCHEMES = Set.of("http", "https");

    private final AwardCategoryRepository categories;
    private final AwardDateRules dates;
    private final RecipientUnits recipients;

    /**
     * The form with surrounding spaces removed and blank texts treated as empty.
     *
     * @param form the form as sent
     * @return the cleaned form
     */
    public AwardForm normalize(AwardForm form) {
        return new AwardForm(clean(form.title()), clean(form.titleUk()), clean(form.description()),
            clean(form.descriptionUk()), form.categoryId(), clean(form.awardingOrganization()), form.awardDate(),
            clean(form.externalUrl()), form.recipientOrganizationId(), form.version());
    }

    /**
     * Checks the fields of a draft; fields that are empty are not checked beyond the title. A recipient unit
     * must be one the caller may enter awards for.
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
        if (form.recipientOrganizationId() != null && !recipients.covers(form.recipientOrganizationId())) {
            errors.add(new FieldViolation(RECIPIENT, "out-of-scope",
                "Awards can be entered only for a faculty or department of your secretary or dean role"));
        }
        if (!errors.isEmpty()) {
            throw ApiProblemException.validationFailed("The award form has invalid fields", errors);
        }
        return category;
    }

    private static List<FieldViolation> textErrors(AwardForm form) {
        List<FieldViolation> errors = new ArrayList<>();
        if (form.title() == null && form.titleUk() == null) {
            errors.add(new FieldViolation(TITLE, "required", "A title in Ukrainian or English is required"));
        }
        tooLong(errors, TITLE, form.title(), TITLE_MAX);
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
     * offered, the date is acceptable and the caller may still enter awards for its recipient unit.
     *
     * @param award the draft
     * @throws ApiProblemException 422 {@code award-incomplete} naming the empty fields,
     *                             {@code validation-failed} for a deactivated category or a refused date, or
     *                             {@code recipient-out-of-scope} for a unit the caller no longer covers
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
            missing.add(required(AwardDateRules.AWARD_DATE));
        }
        if (!missing.isEmpty()) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "award-incomplete",
                "The award is missing fields a submission needs", Map.of("errors", missing));
        }
        List<FieldViolation> errors = new ArrayList<>();
        if (!award.getCategory().isActive()) {
            errors.add(inactiveCategory());
        }
        dates.check(award.getAwardDate()).ifPresent(errors::add);
        if (!errors.isEmpty()) {
            throw ApiProblemException.validationFailed("The award has invalid fields", errors);
        }
        requireRecipientInScope(award);
    }

    private void requireRecipientInScope(Award award) {
        if (award.isUnitAward() && !recipients.covers(award.getRecipientOrganizationId())) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "recipient-out-of-scope",
                "You can no longer submit awards for this faculty or department",
                Map.of(RECIPIENT, award.getRecipientOrganizationId()));
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

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
