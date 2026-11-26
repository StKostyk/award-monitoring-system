package ua.edu.chnu.awards.award.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.award.AwardDocuments;
import ua.edu.chnu.awards.award.dto.Achievement;
import ua.edu.chnu.awards.award.dto.AchievementDetail;
import ua.edu.chnu.awards.award.dto.AchievementQuery;
import ua.edu.chnu.awards.award.dto.AchievementRecipient;
import ua.edu.chnu.awards.award.dto.RecipientType;
import ua.edu.chnu.awards.award.dto.SharedDocument;
import ua.edu.chnu.awards.award.dto.UnitRef;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.AwardVisibility;
import ua.edu.chnu.awards.award.mapper.AwardMapper;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.award.repository.AwardSpecifications;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.organization.service.OrganizationLookup;

import lombok.RequiredArgsConstructor;

/**
 * The approved awards their owners shared and the approved awards of faculties and departments.
 */
@Service
@RequiredArgsConstructor
public class Achievements {

    private static final Set<AwardVisibility> COLLEAGUES = EnumSet.of(AwardVisibility.UNIVERSITY,
        AwardVisibility.PUBLIC);
    private static final Set<AwardVisibility> EVERYONE = EnumSet.of(AwardVisibility.PUBLIC);

    private final AwardRepository awards;
    private final OrganizationLookup organizations;
    private final AwardSpecifications specifications;
    private final AwardDocuments documents;
    private final AwardSearch search;

    /**
     * A page of the achievements colleagues may see, newest award date first.
     *
     * @param query filters
     * @param page  0-based page
     * @param size  page size, capped at 100
     * @return the page
     * @throws UnitNotFoundException when the unit is not a faculty or department
     * @throws ApiProblemException   400 {@code invalid-parameter} for a year outside 1950..2100
     */
    @Transactional(readOnly = true)
    public Page<Achievement> shared(AchievementQuery query, int page, int size) {
        return page(COLLEAGUES, query, page, size);
    }

    /**
     * A page of the achievements anyone may see: public personal awards and unit awards, newest award date first.
     *
     * @param query filters
     * @param page  0-based page
     * @param size  page size, capped at 100
     * @return the page
     * @throws UnitNotFoundException when the unit is not a faculty or department
     * @throws ApiProblemException   400 {@code invalid-parameter} for a year outside 1950..2100
     */
    @Transactional(readOnly = true)
    public Page<Achievement> published(AchievementQuery query, int page, int size) {
        return page(EVERYONE, query, page, size);
    }

    /**
     * One shared award as a colleague sees it, with the documents its owner shows to colleagues.
     *
     * @param id the award
     * @return the achievement and its shown documents
     * @throws AwardNotFoundException when it does not exist or is not shared with colleagues
     */
    @Transactional(readOnly = true)
    public AchievementDetail detail(long id) {
        Award award = awards.findWithDetailsById(id)
            .filter(found -> found.isSharedWithColleagues()
                || found.isUnitAward() && found.getStatus() == AwardStatus.APPROVED)
            .orElseThrow(() -> new AwardNotFoundException(id));
        List<SharedDocument> shown = award.isUnitAward() ? List.of()
            : documents.shownToColleagues(id);
        return new AchievementDetail(of(award), shown);
    }

    private Page<Achievement> page(Set<AwardVisibility> visibilities, AchievementQuery query, int page, int size) {
        requireValid(query);
        return search.page(specifications.shared(visibilities, query), query.q(),
            Sort.by(Sort.Order.desc("awardDate"), Sort.Order.desc("id")), true, page, size).map(Achievements::of);
    }

    private void requireValid(AchievementQuery query) {
        YearRange.requireYear(query.year(), "year");
        if (query.unit() != null) {
            organizations.findById(query.unit())
                .filter(unit -> RecipientUnits.TYPES.contains(unit.getOrgType()))
                .orElseThrow(() -> new UnitNotFoundException(query.unit()));
        }
    }

    private static Achievement of(Award award) {
        AchievementRecipient recipient = award.isUnitAward()
            ? new AchievementRecipient(RecipientType.UNIT, null, UnitRef.of(award.getOrganization()))
            : new AchievementRecipient(RecipientType.PERSON, award.getOwner().getFullName(),
                UnitRef.of(award.getOrganization()));
        return new Achievement(award.getId(), award.getTitle(), award.getTitleUk(), award.getDescription(),
            award.getDescriptionUk(), AwardMapper.categoryRef(award.getCategory()), award.getAwardingOrganization(),
            award.getAwardDate(), award.getExternalUrl(), award.isVerificationBadge(), recipient);
    }
}
