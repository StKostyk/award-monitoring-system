package ua.edu.chnu.awards.award.service;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.award.dto.Achievement;
import ua.edu.chnu.awards.award.dto.AchievementQuery;
import ua.edu.chnu.awards.award.dto.AchievementRecipient;
import ua.edu.chnu.awards.award.dto.RecipientType;
import ua.edu.chnu.awards.award.dto.UnitRef;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardVisibility;
import ua.edu.chnu.awards.award.mapper.AwardMapper;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.award.repository.AwardSpecifications;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.PageResponse;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;

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
    private static final int FIRST_YEAR = 1950;
    private static final int LAST_YEAR = 2100;

    private final AwardRepository awards;
    private final OrganizationRepository organizations;
    private final AwardSpecifications specifications;

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

    private Page<Achievement> page(Set<AwardVisibility> visibilities, AchievementQuery query, int page, int size) {
        requireValid(query);
        PageRequest pageable = PageResponse.request(page, size,
            Sort.by(Sort.Order.desc("awardDate"), Sort.Order.desc("id")));
        return awards.findAll(specifications.shared(visibilities, query), pageable).map(Achievements::of);
    }

    private void requireValid(AchievementQuery query) {
        if (query.year() != null && (query.year() < FIRST_YEAR || query.year() > LAST_YEAR)) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-parameter",
                "The year must lie between " + FIRST_YEAR + " and " + LAST_YEAR, Map.of("parameter", "year"));
        }
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
