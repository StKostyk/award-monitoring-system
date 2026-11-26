package ua.edu.chnu.awards.analytics.service;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.analytics.config.AnalyticsProperties;
import ua.edu.chnu.awards.analytics.dto.TopPerformers;
import ua.edu.chnu.awards.analytics.dto.UnitFigures;
import ua.edu.chnu.awards.award.dto.OwnerAwardCount;
import ua.edu.chnu.awards.gdpr.ConsentLookup;
import ua.edu.chnu.awards.gdpr.dto.ConsentType;
import ua.edu.chnu.awards.organization.dto.OrganizationRef;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;

import lombok.RequiredArgsConstructor;

/**
 * Names the owners with the most counted personal awards of a unit, at every request, so a withdrawn consent or a
 * closed account leaves the list at once even while the counts are cached.
 */
@Component
@RequiredArgsConstructor
public class TopPerformersRanking {

    private static final Collator UKRAINIAN = Collator.getInstance(Locale.forLanguageTag("uk"));

    private final ConsentLookup consents;
    private final UserAccounts users;
    private final AnalyticsProperties properties;

    /**
     * The top performers of a unit: active owners whose newest analytics record is a grant, by count, then by
     * surname, without positions.
     *
     * @param figures the aggregates of the unit
     * @param members active accounts of the unit
     * @return the people, or {@code GROUP_TOO_SMALL} below the minimum group
     */
    public TopPerformers of(UnitFigures figures, long members) {
        if (members < properties.minGroupSize()) {
            return new TopPerformers(TopPerformers.Status.GROUP_TOO_SMALL, List.of());
        }
        Map<Long, OwnerAwardCount> owners = UnitFiguresHelper.currentOwners(figures);
        Set<Long> consenting = consents.grantedAmong(owners.keySet(), ConsentType.ANALYTICS);
        Comparator<TopPerformers.Person> order = Comparator.comparingLong(TopPerformers.Person::count).reversed()
            .thenComparing(TopPerformers.Person::lastName, UKRAINIAN)
            .thenComparing(TopPerformers.Person::firstName, UKRAINIAN)
            .thenComparingLong(TopPerformers.Person::userId);
        List<TopPerformers.Person> people = users.findAllById(consenting).stream()
            .filter(user -> user.getAccountStatus() == AccountStatus.ACTIVE)
            .map(user -> person(user, owners.get(user.getId())))
            .sorted(order).limit(properties.topPerformers()).toList();
        return new TopPerformers(TopPerformers.Status.LISTED, people);
    }

    private static TopPerformers.Person person(User user, OwnerAwardCount owner) {
        Organization unit = user.getOrganization();
        OrganizationRef department = unit == null ? null
            : new OrganizationRef(unit.getId(), unit.getName(), unit.getNameUk(), unit.getCode(), unit.getOrgType());
        return new TopPerformers.Person(user.getId(), user.getFirstName(), user.getLastName(), department,
            owner.count(), owner.highestLevel());
    }
}
