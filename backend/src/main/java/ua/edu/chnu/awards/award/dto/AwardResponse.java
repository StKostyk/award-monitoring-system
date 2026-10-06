package ua.edu.chnu.awards.award.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.user.dto.OrganizationRef;

/**
 * An award as the API shows it.
 *
 * @param id                   identifier
 * @param title                English title
 * @param titleUk              Ukrainian title
 * @param description          English description
 * @param descriptionUk        Ukrainian description
 * @param category             category, null while not chosen
 * @param awardingOrganization who granted the award
 * @param awardDate            when it was granted
 * @param externalUrl          link to the award
 * @param status               status of the award
 * @param impactScore          impact score, set at submission
 * @param owner                who received a personal award or entered a unit award
 * @param recipient            the owner, or the faculty or department that received the award
 * @param organization         the owner's department at submission, or the recipient unit
 * @param request              approval request, null for a draft
 * @param warnings             hints about the data that do not block saving
 * @param createdAt            when it was created
 * @param updatedAt            when it was last changed
 * @param version              optimistic-lock version
 */
@SuppressWarnings("PMD.CommentSize")
public record AwardResponse(Long id, String title, String titleUk, String description, String descriptionUk,
                            CategoryRef category, String awardingOrganization, LocalDate awardDate,
                            String externalUrl, AwardStatus status, Integer impactScore, UserRef owner,
                            AwardRecipient recipient, OrganizationRef organization, RequestSummary request,
                            List<AwardWarning> warnings, Instant createdAt, Instant updatedAt, Long version) {
}
