package ua.edu.chnu.awards.award.entity;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * The business fields of an award at one version.
 *
 * @param title                English title
 * @param titleUk              Ukrainian title
 * @param description          English description
 * @param descriptionUk        Ukrainian description
 * @param awardingOrganization who granted the award
 * @param awardDate            date of the award
 * @param categoryId           category, null when not chosen
 * @param status               award status
 * @param impactScore          score set at submission
 * @param verificationBadge    whether the award carries the verification badge
 * @param externalUrl          link to a public record
 * @param organizationId       department the award belongs to
 */
public record AwardSnapshot(String title, String titleUk, String description, String descriptionUk,
                            String awardingOrganization,
                            @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate awardDate, Long categoryId,
                            AwardStatus status, Integer impactScore, boolean verificationBadge,
                            String externalUrl, Long organizationId) {

    /**
     * The current state of an award.
     *
     * @param award the award
     * @return its snapshot
     */
    public static AwardSnapshot of(Award award) {
        return new AwardSnapshot(award.getTitle(), award.getTitleUk(), award.getDescription(),
            award.getDescriptionUk(), award.getAwardingOrganization(), award.getAwardDate(),
            award.getCategory() == null ? null : award.getCategory().getId(), award.getStatus(),
            award.getImpactScore(), award.isVerificationBadge(), award.getExternalUrl(),
            award.getOrganization().getId());
    }

    /**
     * The fields by name, in declaration order.
     *
     * @return field name to value, values may be null
     */
    public Map<String, Object> fields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("title", title);
        fields.put("titleUk", titleUk);
        fields.put("description", description);
        fields.put("descriptionUk", descriptionUk);
        fields.put("awardingOrganization", awardingOrganization);
        fields.put("awardDate", awardDate);
        fields.put("categoryId", categoryId);
        fields.put("status", status);
        fields.put("impactScore", impactScore);
        fields.put("verificationBadge", verificationBadge);
        fields.put("externalUrl", externalUrl);
        fields.put("organizationId", organizationId);
        return fields;
    }
}
