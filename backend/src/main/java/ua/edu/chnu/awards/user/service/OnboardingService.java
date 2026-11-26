package ua.edu.chnu.awards.user.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.authz.RolePermissions;
import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.user.dto.OnboardingFacts;
import ua.edu.chnu.awards.user.dto.OnboardingRequest;
import ua.edu.chnu.awards.user.dto.OnboardingState;
import ua.edu.chnu.awards.user.dto.OnboardingStep;
import ua.edu.chnu.awards.user.dto.OnboardingStepKey;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.repository.OnboardingQueries;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * The getting-started steps of a person: which ones the roles the person holds call for, which ones the data
 * proves and which ones the person marked done. A display aid, so nothing is audited.
 */
@Service
@RequiredArgsConstructor
public class OnboardingService {

    static final String CREATE = "award:create";
    static final String REVIEW = "award:approve:level1";

    private static final Set<OnboardingStepKey> DERIVED =
        EnumSet.of(OnboardingStepKey.NOTICE, OnboardingStepKey.FIRST_AWARD, OnboardingStepKey.DELEGATE);
    private static final Set<String> NAMES = Arrays.stream(OnboardingStepKey.values()).map(Enum::name)
        .collect(Collectors.toUnmodifiableSet());
    private static final String DONE = "done";
    private static final OnboardingRequest NONE = new OnboardingRequest(List.of(), false, false);

    private final OnboardingQueries queries;
    private final UserRoleRepository roles;
    private final RolePermissions permissions;
    private final Clock clock;

    /**
     * The steps of a person with their state; nothing is done or hidden until the person saved progress, except
     * what the data proves.
     *
     * @param userId the person
     * @return the steps, whether the checklist was hidden and whether the tour was seen
     */
    @Transactional(readOnly = true)
    public OnboardingState state(long userId) {
        return stateOf(stepsOf(userId), queries.facts(userId), queries.find(userId).orElse(NONE));
    }

    /**
     * Replaces the stored progress; steps the data decides are accepted and not stored.
     *
     * @param userId the person
     * @param update the steps marked done, the hidden checklist and the seen tour
     * @return the steps after the save
     */
    @Transactional
    public OnboardingState save(long userId, OnboardingRequest update) {
        List<OnboardingStepKey> steps = stepsOf(userId);
        Set<OnboardingStepKey> done = EnumSet.noneOf(OnboardingStepKey.class);
        for (String name : update.done()) {
            if (name == null || !NAMES.contains(name) || !steps.contains(OnboardingStepKey.valueOf(name))
                || !done.add(OnboardingStepKey.valueOf(name))) {
                throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-parameter",
                    "The value of " + DONE + " is not valid", Map.of("parameter", DONE));
            }
        }
        done.removeAll(DERIVED);
        OnboardingRequest stored = new OnboardingRequest(done.stream().map(Enum::name).toList(), update.dismissed(),
            update.tourSeen());
        queries.save(userId, stored);
        return stateOf(steps, queries.facts(userId), stored);
    }

    private List<OnboardingStepKey> stepsOf(long userId) {
        Set<RoleType> held = roles.findCurrentByUserId(userId, LocalDate.now(clock)).stream()
            .map(UserRole::getRoleType).collect(Collectors.toSet());
        List<String> granted = permissions.union(held);
        return Arrays.stream(OnboardingStepKey.values()).filter(key -> applies(key, held, granted)).toList();
    }

    private static boolean applies(OnboardingStepKey key, Set<RoleType> held, Collection<String> granted) {
        return switch (key) {
            case NOTICE, PROFILE, NOTIFICATIONS -> true;
            case FIRST_AWARD, VISIBILITY -> granted.contains(CREATE);
            case REVIEW_QUEUE -> granted.contains(REVIEW);
            case DELEGATE -> held.contains(RoleType.DEAN) || held.contains(RoleType.RECTOR);
            case IMPORT -> held.contains(RoleType.FACULTY_SECRETARY);
        };
    }

    private static OnboardingState stateOf(List<OnboardingStepKey> steps, OnboardingFacts facts,
                                           OnboardingRequest progress) {
        List<OnboardingStep> listed = steps.stream().map(key -> new OnboardingStep(key,
            DERIVED.contains(key) ? proven(key, facts) : progress.done().contains(key.name()),
            DERIVED.contains(key))).toList();
        return new OnboardingState(listed, progress.dismissed(), progress.tourSeen());
    }

    private static boolean proven(OnboardingStepKey key, OnboardingFacts facts) {
        return switch (key) {
            case NOTICE -> facts.noticeAcknowledged();
            case FIRST_AWARD -> facts.ownsAward();
            default -> facts.gaveDelegation();
        };
    }
}
