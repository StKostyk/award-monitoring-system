package ua.edu.chnu.awards.award.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.dto.Person;
import ua.edu.chnu.awards.award.dto.PersonDetail;
import ua.edu.chnu.awards.award.dto.UserRef;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.mapper.AwardMapper;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.organization.dto.OrganizationRef;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;

import lombok.RequiredArgsConstructor;

/**
 * Builds the answers about people without an account: the person with the address in full or masked, and the
 * person with their awards.
 */
@Component
@RequiredArgsConstructor
public class PersonViews {

    private final AwardRepository awards;
    private final RequestLookup requests;
    private final AwardMapper mapper;
    private final UserAccounts users;

    /**
     * The person with the address in full.
     *
     * @param person the person
     * @return the answer
     */
    public Person person(User person) {
        return view(person, false, counts(List.of(person.getId())), adders(List.of(person)));
    }

    /**
     * The people with masked addresses, as lists and duplicate matches show them.
     *
     * @param people the people
     * @return the answers in the same order
     */
    public List<Person> masked(List<User> people) {
        Map<Long, Long> counts = counts(people.stream().map(User::getId).toList());
        Map<Long, User> adders = adders(people);
        return people.stream().map(person -> view(person, true, counts, adders)).toList();
    }

    /**
     * The person with the address in full and every award, newest first.
     *
     * @param person the person
     * @return the answer
     */
    public PersonDetail detail(User person) {
        List<Award> owned = awards.findByOwnerIdOrderByCreatedAtDescIdDesc(person.getId());
        Map<Long, AwardRequest> byAward = requests.byAward(owned.stream().map(Award::getId).toList());
        return new PersonDetail(person(person), owned.stream()
            .map(award -> mapper.toResponse(award, byAward.get(award.getId()), List.of()))
            .toList());
    }

    private Person view(User person, boolean mask, Map<Long, Long> counts, Map<Long, User> adders) {
        Organization unit = person.getOrganization();
        String email = person.hasMailbox() ? person.getEmailAddress() : null;
        return new Person(person.getId(), person.getFirstName(), person.getLastName(),
            mask ? PersonRules.mask(email) : email,
            new OrganizationRef(unit.getId(), unit.getName(), unit.getNameUk(), unit.getCode(), unit.getOrgType()),
            person.getAccountStatus(), UserRef.of(adders.get(person.getAddedById())), person.getInvitedAt(),
            counts.getOrDefault(person.getId(), 0L), person.getVersion());
    }

    private Map<Long, Long> counts(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return awards.countByOwners(ids).stream()
            .collect(Collectors.toMap(AwardRepository.OwnerTotal::getOwnerId, AwardRepository.OwnerTotal::getTotal));
    }

    private Map<Long, User> adders(List<User> people) {
        List<Long> ids = people.stream().map(User::getAddedById).filter(Objects::nonNull).distinct().toList();
        return users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }
}
