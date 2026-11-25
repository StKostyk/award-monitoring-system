package ua.edu.chnu.awards.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

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

class OnboardingServiceTest {

    private static final long PERSON = 5L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneOffset.UTC);
    private static final OnboardingFacts NOTHING = new OnboardingFacts(false, false, false);

    private final OnboardingQueries queries = mock(OnboardingQueries.class);
    private final UserRoleRepository roles = mock(UserRoleRepository.class);
    private final OnboardingService service = new OnboardingService(queries, roles, new RolePermissions(), CLOCK);

    @Test
    void ac9_1_anEmployeeHasTheFiveCommonStepsInOrder() {
        holding(RoleType.EMPLOYEE);

        assertThat(keys(service.state(PERSON))).containsExactly("NOTICE", "PROFILE", "FIRST_AWARD", "VISIBILITY",
            "NOTIFICATIONS");
    }

    @Test
    void ac9_1_aDeanAddsTheReviewQueueAndTheDelegation() {
        holding(RoleType.DEAN);

        assertThat(keys(service.state(PERSON))).containsExactly("NOTICE", "PROFILE", "FIRST_AWARD", "VISIBILITY",
            "NOTIFICATIONS", "REVIEW_QUEUE", "DELEGATE");
    }

    @Test
    void ac9_1_aFacultySecretaryAddsTheReviewQueueAndTheImport() {
        holding(RoleType.FACULTY_SECRETARY);

        assertThat(keys(service.state(PERSON))).containsExactly("NOTICE", "PROFILE", "FIRST_AWARD", "VISIBILITY",
            "NOTIFICATIONS", "REVIEW_QUEUE", "IMPORT");
    }

    @Test
    void ac9_1_anAdministratorAndAnOfficerWithoutAwardCreateHaveThreeSteps() {
        holding(RoleType.SYSTEM_ADMIN, RoleType.GDPR_OFFICER);

        assertThat(keys(service.state(PERSON))).containsExactly("NOTICE", "PROFILE", "NOTIFICATIONS");
    }

    @Test
    void ac9_2_theNoticeTheFirstAwardAndTheDelegationAreDoneFromTheDataWithoutBeingStored() {
        holding(RoleType.RECTOR);
        when(queries.facts(PERSON)).thenReturn(new OnboardingFacts(true, true, true));

        OnboardingState state = service.state(PERSON);

        assertThat(state.steps()).filteredOn(OnboardingStep::derived).extracting(OnboardingStep::key)
            .containsExactly(OnboardingStepKey.NOTICE, OnboardingStepKey.FIRST_AWARD, OnboardingStepKey.DELEGATE);
        assertThat(state.steps()).filteredOn(OnboardingStep::derived).allMatch(OnboardingStep::done);
        assertThat(state.steps()).filteredOn(step -> !step.derived()).noneMatch(OnboardingStep::done);
        assertThat(state.dismissed()).isFalse();
        assertThat(state.tourSeen()).isFalse();
    }

    @Test
    void ac9_3_storedStepsAreDoneAndStoredKeysOfAnotherRoleAreIgnored() {
        holding(RoleType.EMPLOYEE);
        when(queries.find(PERSON)).thenReturn(Optional.of(
            new OnboardingRequest(List.of("PROFILE", "IMPORT", "GONE"), true, true)));

        OnboardingState state = service.state(PERSON);

        assertThat(state.steps()).filteredOn(OnboardingStep::done).extracting(OnboardingStep::key)
            .containsExactly(OnboardingStepKey.PROFILE);
        assertThat(state.dismissed()).isTrue();
        assertThat(state.tourSeen()).isTrue();
    }

    @Test
    void ac9_3_aSaveStoresTheStoredStepsOnlyAndAnswersTheNewState() {
        holding(RoleType.EMPLOYEE);

        OnboardingState state = service.save(PERSON,
            new OnboardingRequest(List.of("NOTICE", "VISIBILITY", "PROFILE"), false, true));

        verify(queries).save(PERSON, new OnboardingRequest(List.of("PROFILE", "VISIBILITY"), false, true));
        assertThat(state.steps()).filteredOn(OnboardingStep::done).extracting(OnboardingStep::key)
            .containsExactly(OnboardingStepKey.PROFILE, OnboardingStepKey.VISIBILITY);
        assertThat(state.tourSeen()).isTrue();
    }

    @Test
    void ac9_3_aStepOutsideTheRolesAnUnknownStepAndADuplicateAreInvalid() {
        holding(RoleType.EMPLOYEE);

        for (List<String> done : List.of(List.of("REVIEW_QUEUE"), List.of("SOMETHING"),
            List.of("PROFILE", "PROFILE"), Arrays.asList("PROFILE", null))) {
            assertThatThrownBy(() -> service.save(PERSON, new OnboardingRequest(done, false, false)))
                .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(problem.getProperties()).containsEntry("parameter", "done");
                });
        }
        verify(queries, never()).save(anyLong(), any());
    }

    @Test
    void ac9_3_aMissingListIsEmpty() {
        assertThat(new OnboardingRequest(null, true, false).done()).isEmpty();
    }

    private void holding(RoleType... types) {
        List<UserRole> held = Arrays.stream(types).map(type -> UserRole.builder().roleType(type).build()).toList();
        when(roles.findCurrentByUserId(PERSON, LocalDate.of(2026, 11, 24))).thenReturn(held);
        when(queries.facts(PERSON)).thenReturn(NOTHING);
        when(queries.find(PERSON)).thenReturn(Optional.empty());
    }

    private static List<String> keys(OnboardingState state) {
        return state.steps().stream().map(step -> step.key().name()).toList();
    }
}
