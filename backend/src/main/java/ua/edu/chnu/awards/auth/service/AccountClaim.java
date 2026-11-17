package ua.edu.chnu.awards.auth.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.entity.TokenPurpose;
import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.service.RoleHolders;
import ua.edu.chnu.awards.user.service.UserAccounts;
import ua.edu.chnu.awards.user.service.WaitingAwards;

import lombok.RequiredArgsConstructor;

/**
 * Hands the row of a person added without an account to whoever registers with the person's address and later
 * proves it: the prepared name, department and awards stay, the registration only brings the password.
 */
@Component
@RequiredArgsConstructor
public class AccountClaim {

    private final UserAccounts users;
    private final RoleHolders roleHolders;
    private final OneTimeTokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final EmailAddressRules addressRules;
    private final AuditService audit;
    private final WaitingAwards waitingAwards;
    private final Clock clock;

    /**
     * Takes over a prepared row for a registration. The row stays taken while a verification link of an earlier
     * claim is still usable.
     *
     * @param prepared the person found by the address
     * @param password the password chosen at registration
     * @return the row, now {@code PENDING}
     */
    public User takeOver(User prepared, String password) {
        User person = users.findByIdForUpdate(prepared.getId()).orElseThrow(() -> addressRules.taken(null));
        boolean usableClaim = person.getAccountStatus() == AccountStatus.PENDING
            && tokens.countUsable(person, TokenPurpose.EMAIL_VERIFICATION) > 0;
        if (usableClaim || !person.isWithoutAccount()) {
            throw addressRules.taken(null);
        }
        person.setAccountStatus(AccountStatus.PENDING);
        person.setPasswordHash(passwordEncoder.encode(password));
        tokens.invalidate(person, TokenPurpose.EMAIL_VERIFICATION);
        audit.record(AuditAction.REGISTRATION_REPLACED, person.getId(),
            Map.of("organizationId", person.getOrganization().getId(), "claim", true));
        return person;
    }

    /**
     * Completes the claim of a just verified account: the person's adding counts as the confirmation of the
     * membership, so the employee role in the prepared department is granted at once.
     *
     * @param user the account that has just been verified
     * @return the awards waiting for the person, 0 for an account that was not prepared
     */
    public long complete(User user) {
        if (user.getAddedById() == null) {
            return 0;
        }
        roleHolders.save(UserRole.builder()
            .user(user)
            .roleType(RoleType.EMPLOYEE)
            .organization(user.getOrganization())
            .validFrom(LocalDate.now(clock))
            .createdBy(users.getReferenceById(user.getAddedById()))
            .build());
        long waiting = waitingAwards.count(user.getId());
        audit.record(AuditAction.ACCOUNT_CLAIMED, user.getId(), Map.of("awards", waiting));
        return waiting;
    }
}
