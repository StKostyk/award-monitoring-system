package ua.edu.chnu.awards.award.dto;

import java.time.LocalDate;

/**
 * The shared projection of an approved award: no e-mail, person id, documents, request, reviewers, comments or
 * impact score.
 *
 * @param awardId              identifier of the award
 * @param title                English title
 * @param titleUk              Ukrainian title
 * @param description          English description
 * @param descriptionUk        Ukrainian description
 * @param category             category with its level
 * @param awardingOrganization who granted the award
 * @param awardDate            when it was granted
 * @param externalUrl          link to the award
 * @param verified             whether the reviewer checked the documents
 * @param recipient            the person or unit that received it
 */
@SuppressWarnings("PMD.CommentSize")
public record Achievement(Long awardId, String title, String titleUk, String description, String descriptionUk,
                          CategoryRef category, String awardingOrganization, LocalDate awardDate,
                          String externalUrl, boolean verified, AchievementRecipient recipient) {
}
