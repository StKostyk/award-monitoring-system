package ua.edu.chnu.awards.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.entity.TokenPurpose;
import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.service.RoleHolders;
import ua.edu.chnu.awards.user.service.UserAccounts;
import ua.edu.chnu.awards.user.service.WaitingAwards;

class AccountClaimTest {

    private final UserAccounts users = mock(UserAccounts.class);
    private final RoleHolders roleHolders = mock(RoleHolders.class);
    private final OneTimeTokenService tokens = mock(OneTimeTokenService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuditService audit = mock(AuditService.class);
    private final WaitingAwards waitingAwards = mock(WaitingAwards.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneId.of("Europe/Kyiv"));
    private final Organization department = Organization.builder().id(65L).orgType(OrganizationType.DEPARTMENT)
        .active(true).build();
    private final AccountClaim claim = new AccountClaim(users, roleHolders, tokens, passwordEncoder,
        new EmailAddressRules(AuthPropertiesFixture.properties()), audit, waitingAwards, clock);

    @Test
    void ac3_1_theRegistrationBringsOnlyThePassword() {
        User person = prepared(AccountStatus.UNCLAIMED);
        when(passwordEncoder.encode("correct-horse-battery")).thenReturn("$2a$12$hash");

        User claimed = claim.takeOver(person, "correct-horse-battery");

        assertThat(claimed.getAccountStatus()).isEqualTo(AccountStatus.PENDING);
        assertThat(claimed.getPasswordHash()).isEqualTo("$2a$12$hash");
        assertThat(claimed.getFirstName()).isEqualTo("Петро");
        assertThat(claimed.getOrganization()).isSameAs(department);
        verify(tokens).invalidate(person, TokenPurpose.EMAIL_VERIFICATION);
        verify(audit).record(AuditAction.REGISTRATION_REPLACED, 7L, Map.of("organizationId", 65L, "claim", true));
    }

    @Test
    void ac3_4_anAbandonedClaimIsTakenOverAgain() {
        User person = prepared(AccountStatus.PENDING);
        when(tokens.countUsable(person, TokenPurpose.EMAIL_VERIFICATION)).thenReturn(0L);

        assertThat(claim.takeOver(person, "correct-horse-battery").getAccountStatus())
            .isEqualTo(AccountStatus.PENDING);
        verify(tokens).invalidate(person, TokenPurpose.EMAIL_VERIFICATION);
    }

    @Test
    void ac3_1_aClaimWithAUsableLinkKeepsTheAddressTaken() {
        User person = prepared(AccountStatus.PENDING);
        when(tokens.countUsable(person, TokenPurpose.EMAIL_VERIFICATION)).thenReturn(1L);

        assertThatThrownBy(() -> claim.takeOver(person, "correct-horse-battery"))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(e.getType()).isEqualTo("email-taken");
            });
        verify(tokens, never()).invalidate(any(), any());
    }

    @Test
    void aRowVerifiedMeanwhileIsTaken() {
        User person = prepared(AccountStatus.UNCLAIMED);
        User locked = prepared(AccountStatus.ACTIVE);
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> claim.takeOver(person, "correct-horse-battery"))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getType()).isEqualTo("email-taken"));
    }

    @Test
    void ac3_2_theVerifiedClaimGetsTheEmployeeRoleInThePreparedDepartment() {
        User person = prepared(AccountStatus.ACTIVE);
        User adder = User.builder().id(3L).build();
        when(users.getReferenceById(3L)).thenReturn(adder);
        when(waitingAwards.count(7L)).thenReturn(2L);

        long waiting = claim.complete(person);

        assertThat(waiting).isEqualTo(2L);
        ArgumentCaptor<UserRole> role = ArgumentCaptor.forClass(UserRole.class);
        verify(roleHolders).save(role.capture());
        assertThat(role.getValue().getRoleType()).isEqualTo(RoleType.EMPLOYEE);
        assertThat(role.getValue().getOrganization()).isSameAs(department);
        assertThat(role.getValue().getValidFrom()).isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(role.getValue().getCreatedBy()).isSameAs(adder);
        verify(audit).record(AuditAction.ACCOUNT_CLAIMED, 7L, Map.of("awards", 2L));
    }

    @Test
    void aSelfRegisteredAccountHasNothingToComplete() {
        User user = User.builder().id(8L).accountStatus(AccountStatus.ACTIVE).build();

        assertThat(claim.complete(user)).isZero();
        verifyNoInteractions(roleHolders, waitingAwards, audit);
    }

    private User prepared(AccountStatus status) {
        User person = User.builder().id(7L).emailAddress("petro@chnu.edu.ua").firstName("Петро")
            .lastName("Підготовлений").passwordHash("$2a$12$placeholder").accountStatus(status).addedById(3L)
            .organization(department).build();
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(person));
        return person;
    }
}
