package ua.edu.chnu.awards.award.dto;

import java.time.LocalDate;

/**
 * The award form as sent on create and update. Every field may be empty in a draft except a title in one
 * language; {@code version} is required on update and ignored on create.
 *
 * @param title                   English title
 * @param titleUk                 Ukrainian title
 * @param description             English description
 * @param descriptionUk           Ukrainian description
 * @param categoryId              category of the catalogue
 * @param awardingOrganization    who granted the award
 * @param awardDate               when it was granted
 * @param externalUrl             http or https link to the award
 * @param recipientOrganizationId faculty or department that received the award, null for the caller
 * @param version                 version last read, for updates
 */
public record AwardForm(String title, String titleUk, String description, String descriptionUk, Long categoryId,
                        String awardingOrganization, LocalDate awardDate, String externalUrl,
                        Long recipientOrganizationId, Long version) {
}
