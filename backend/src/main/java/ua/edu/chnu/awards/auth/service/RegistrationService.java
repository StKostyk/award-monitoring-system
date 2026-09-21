package ua.edu.chnu.awards.auth.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.dto.RegisterRequest;
import ua.edu.chnu.awards.auth.dto.RegistrationResponse;
import ua.edu.chnu.awards.auth.entity.TokenPurpose;
import ua.edu.chnu.awards.auth.event.VerificationRequested;
import ua.edu.chnu.awards.common.EmailUtils;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.config.AuthProperties;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Self-registration with an institutional address, email verification and re-sending of the link.
 */
@Service
@RequiredArgsConstructor
public class RegistrationService {

    static final String RESEND_KEY_PREFIX = "auth:resend:";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final OrganizationRepository organizationRepository;
    private final OneTimeTokenService tokens;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final RequestThrottle throttle;
    private final AuthProperties properties;
    private final AuditService audit;
    private final Clock clock;

    /**
     * Creates a pending account and requests the verification email.
     *
     * @param request registration data
     * @return the address and its {@code PENDING} status
     */
    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        String email = EmailUtils.normalize(request.email());
        requireInstitutionalDomain(email);
        passwordPolicy.require(request.password());
        if (userRepository.existsByEmailAddressIgnoreCase(email)) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "email-taken",
                "An account with this address already exists");
        }
        Organization department = organizationRepository.findById(request.organizationId())
            .filter(org -> org.getOrgType() == OrganizationType.DEPARTMENT && org.isActive())
            .orElseThrow(() -> new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "organisation-invalid",
                "Choose an active department"));

        User user;
        try {
            user = userRepository.saveAndFlush(User.builder()
                .emailAddress(email)
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .accountStatus(AccountStatus.PENDING)
                .organization(department)
                .build());
        } catch (DataIntegrityViolationException e) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "email-taken",
                "An account with this address already exists", e);
        }
        userRoleRepository.save(UserRole.builder()
            .user(user)
            .roleType(RoleType.EMPLOYEE)
            .organization(department)
            .validFrom(LocalDate.now(clock))
            .build());
        throttle.claim(RESEND_KEY_PREFIX + email, properties.resendInterval());
        sendVerification(user);
        return new RegistrationResponse(user.getEmailAddress(), user.getAccountStatus());
    }

    /**
     * Activates the account behind a verification token once the registration password is presented, so that
     * only the person who registered can complete the registration.
     *
     * @param rawToken token from the link
     * @param password the password chosen at registration
     * @return the address and its new status
     */
    @Transactional
    public RegistrationResponse verify(String rawToken, String password) {
        User owner = tokens.peekOwner(rawToken, TokenPurpose.EMAIL_VERIFICATION);
        if (!passwordEncoder.matches(password, owner.getPasswordHash())) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "password-mismatch",
                "The password does not match the one chosen at registration");
        }
        User user = tokens.redeemOwner(rawToken, TokenPurpose.EMAIL_VERIFICATION);
        if (user.getAccountStatus() != AccountStatus.PENDING) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "account-not-pending",
                "The account is not awaiting verification");
        }
        user.setAccountStatus(AccountStatus.ACTIVE);
        audit.record(AuditAction.EMAIL_VERIFIED, user.getId());
        return new RegistrationResponse(user.getEmailAddress(), user.getAccountStatus());
    }

    /**
     * Sends a fresh verification link to a pending account, at most once per interval. Unknown or already
     * verified addresses are ignored so the response does not reveal whether an account exists.
     *
     * @param email the address
     */
    @Transactional
    public void resend(String email) {
        String normalized = EmailUtils.normalize(email);
        if (!throttle.claim(RESEND_KEY_PREFIX + normalized, properties.resendInterval())) {
            throw new ApiProblemException(HttpStatus.TOO_MANY_REQUESTS, "too-many-requests",
                "A verification email was sent recently; try again in a minute");
        }
        userRepository.findByEmailAddressIgnoreCase(normalized)
            .filter(user -> user.getAccountStatus() == AccountStatus.PENDING)
            .ifPresent(this::sendVerification);
    }

    private void requireInstitutionalDomain(String email) {
        String domain = email.substring(email.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        boolean allowed = properties.allowedEmailDomains().stream()
            .anyMatch(d -> d.equalsIgnoreCase(domain));
        if (!allowed) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "institutional-email-required",
                "Registration is open to institutional addresses only; ask your faculty secretary for help");
        }
    }

    private void sendVerification(User user) {
        String raw = tokens.issue(user, TokenPurpose.EMAIL_VERIFICATION, properties.verificationTtl());
        String link = properties.link("/verify-email", raw);
        events.publishEvent(new VerificationRequested(user.getEmailAddress(), user.getFirstName(), link));
    }
}
