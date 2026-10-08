package ua.edu.chnu.awards.award.dto;

import java.time.LocalDate;
import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A reviewer's correction of a pending award. JSON is read through the setters, so a field left out of the body
 * stays null here and keeps its value, while a field sent as {@code null} becomes an empty {@link Optional} and
 * clears the value.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("PMD.DataClass")
public class AwardCorrectionRequest {

    private Optional<String> title;
    private Optional<String> titleUk;
    private Optional<String> description;
    private Optional<String> descriptionUk;
    private Optional<Long> categoryId;
    private Optional<String> awardingOrganization;
    private Optional<LocalDate> awardDate;
    private Optional<String> externalUrl;
    @Setter
    private Long version;
    @Setter
    private Long requestVersion;
    @Setter
    private String reason;

    /**
     * The award form with the sent fields laid over the current ones.
     *
     * @param current the award's current form
     * @return the form after the correction, before cleaning
     */
    public AwardForm over(AwardForm current) {
        return new AwardForm(pick(title, current.title()), pick(titleUk, current.titleUk()),
            pick(description, current.description()), pick(descriptionUk, current.descriptionUk()),
            pick(categoryId, current.categoryId()), pick(awardingOrganization, current.awardingOrganization()),
            pick(awardDate, current.awardDate()), pick(externalUrl, current.externalUrl()), null, version);
    }

    /**
     * Sets the English title.
     *
     * @param value the title, null to clear it
     */
    public void setTitle(String value) {
        title = Optional.ofNullable(value);
    }

    /**
     * Sets the Ukrainian title.
     *
     * @param value the title, null to clear it
     */
    public void setTitleUk(String value) {
        titleUk = Optional.ofNullable(value);
    }

    /**
     * Sets the English description.
     *
     * @param value the description, null to clear it
     */
    public void setDescription(String value) {
        description = Optional.ofNullable(value);
    }

    /**
     * Sets the Ukrainian description.
     *
     * @param value the description, null to clear it
     */
    public void setDescriptionUk(String value) {
        descriptionUk = Optional.ofNullable(value);
    }

    /**
     * Sets the category of the catalogue.
     *
     * @param value the category, null to clear it
     */
    public void setCategoryId(Long value) {
        categoryId = Optional.ofNullable(value);
    }

    /**
     * Sets who granted the award.
     *
     * @param value the organisation, null to clear it
     */
    public void setAwardingOrganization(String value) {
        awardingOrganization = Optional.ofNullable(value);
    }

    /**
     * Sets when the award was granted.
     *
     * @param value the date, null to clear it
     */
    public void setAwardDate(LocalDate value) {
        awardDate = Optional.ofNullable(value);
    }

    /**
     * Sets the http or https link to the award.
     *
     * @param value the link, null to clear it
     */
    public void setExternalUrl(String value) {
        externalUrl = Optional.ofNullable(value);
    }

    private static <T> T pick(Optional<T> sent, T current) {
        return sent == null ? current : sent.orElse(null);
    }
}
