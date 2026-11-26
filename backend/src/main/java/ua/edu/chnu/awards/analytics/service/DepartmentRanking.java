package ua.edu.chnu.awards.analytics.service;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.analytics.config.AnalyticsProperties;
import ua.edu.chnu.awards.analytics.dto.PersonalRank;
import ua.edu.chnu.awards.analytics.dto.RankStatus;
import ua.edu.chnu.awards.award.dto.DepartmentStanding;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.award.service.AwardStatistics;
import ua.edu.chnu.awards.gdpr.ConsentLookup;
import ua.edu.chnu.awards.gdpr.dto.ConsentType;
import ua.edu.chnu.awards.organization.dto.OrganizationRef;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;
import ua.edu.chnu.awards.user.service.UserNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * The rank of a person among the active accounts of the department, shown only with the person's analytics consent,
 * an active account and a large enough group.
 */
@Component
@RequiredArgsConstructor
public class DepartmentRanking {

    private final AwardStatistics statistics;
    private final ConsentLookup consents;
    private final UserAccounts users;
    private final AnalyticsProperties properties;

    /**
     * Ranks a person's count of counted awards by competition ranking.
     *
     * @param userId the person
     * @param count  the person's counted awards in the period
     * @param period the calendar years
     * @return the rank, with figures only when ranked
     */
    public PersonalRank of(long userId, long count, YearRange period) {
        if (!consents.granted(userId, ConsentType.ANALYTICS)) {
            return PersonalRank.without(RankStatus.CONSENT_REQUIRED);
        }
        User caller = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        Organization unit = caller.getOrganization();
        if (caller.getAccountStatus() != AccountStatus.ACTIVE || unit.getOrgType() != OrganizationType.DEPARTMENT) {
            return PersonalRank.without(RankStatus.NO_DEPARTMENT);
        }
        OrganizationRef department = new OrganizationRef(unit.getId(), unit.getName(), unit.getNameUk(),
            unit.getCode(), unit.getOrgType());
        DepartmentStanding standing = statistics.standing(unit.getId(), userId, count, period);
        if (standing.groupSize() < properties.minGroupSize()) {
            return new PersonalRank(RankStatus.GROUP_TOO_SMALL, null, null, null, department);
        }
        return new PersonalRank(RankStatus.RANKED, Math.toIntExact(standing.ahead() + 1),
            Math.toIntExact(standing.groupSize()), Math.toIntExact(standing.tied()), department);
    }
}
