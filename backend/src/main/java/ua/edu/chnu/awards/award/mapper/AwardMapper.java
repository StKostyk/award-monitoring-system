package ua.edu.chnu.awards.award.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.dto.AwardRecipient;
import ua.edu.chnu.awards.award.dto.AwardResponse;
import ua.edu.chnu.awards.award.dto.AwardWarning;
import ua.edu.chnu.awards.award.dto.CategoryRef;
import ua.edu.chnu.awards.award.dto.RequestSummary;
import ua.edu.chnu.awards.award.dto.UserRef;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardCategory;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.service.StatusEstimator;
import ua.edu.chnu.awards.user.dto.OrganizationRef;
import ua.edu.chnu.awards.user.entity.Organization;

import lombok.RequiredArgsConstructor;

/**
 * Converts awards to API responses.
 */
@Component
@RequiredArgsConstructor
public class AwardMapper {

    private final StatusEstimator estimator;

    /**
     * Builds the response of an award without warnings.
     *
     * @param award   the award
     * @param request its approval request, null for a draft
     * @return response
     */
    public AwardResponse toResponse(Award award, AwardRequest request) {
        return toResponse(award, request, List.of());
    }

    /**
     * Builds the response of an award.
     *
     * @param award    the award
     * @param request  its approval request, null for a draft
     * @param warnings hints about the award data
     * @return response
     */
    public AwardResponse toResponse(Award award, AwardRequest request, List<AwardWarning> warnings) {
        return new AwardResponse(award.getId(), award.getTitle(), award.getTitleUk(), award.getDescription(),
            award.getDescriptionUk(), categoryRef(award.getCategory()), award.getAwardingOrganization(),
            award.getAwardDate(), award.getExternalUrl(), award.getStatus(), award.getImpactScore(),
            UserRef.of(award.getOwner()), recipient(award), organizationRef(award.getOrganization()),
            toSummary(award, request), List.copyOf(warnings), award.getCreatedAt(), award.getUpdatedAt(),
            award.getVersion());
    }

    private static AwardRecipient recipient(Award award) {
        return award.isUnitAward() ? AwardRecipient.unit(award.getOrganization()) : AwardRecipient.PERSON;
    }

    private static CategoryRef categoryRef(AwardCategory category) {
        return category == null ? null
            : new CategoryRef(category.getId(), category.getName(), category.getNameUk(), category.getLevel());
    }

    private static OrganizationRef organizationRef(Organization organization) {
        return new OrganizationRef(organization.getId(), organization.getName(), organization.getNameUk(),
            organization.getCode(), organization.getOrgType());
    }

    private RequestSummary toSummary(Award award, AwardRequest request) {
        if (request == null) {
            return null;
        }
        StatusEstimator.Timeline timeline = estimator.timeline(award, request);
        return new RequestSummary(request.getStatus(), request.getCurrentLevel(), request.getSubmittedAt(),
            timeline.deadline(), timeline.estimatedCompletion(), timeline.overdue());
    }
}
