package ua.edu.chnu.awards.auth.service;

import java.util.Locale;
import java.util.Map;

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
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;
import ua.edu.chnu.awards.user.service.MembershipConfirmation;

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
    private final MembershipConfirmation membership;

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
        Organization department = organizationRepository.findById(request.organizationId())
            .filter(org -> org.getOrgType() == OrganizationType.DEPARTMENT && org.isActive())
            .orElseThrow(() -> new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "organisation-invalid",
                "Choose an active department"));
        User user = userRepository.findByEmailAddressIgnoreCase(email)
            .map(existing -> replaceAbandoned(existing, request, department))
            .orElseGet(() -> create(email, request, department));

        membership.confirm(user, department).ifPresent(userRoleRepository::save);
        throttle.claim(RESEND_KEY_PREFIX + email, properties.resendInterval());
        sendVerification(user);
        return new RegistrationResponse(user.getEmailAddress(), user.getAccountStatus());
    }

    private User create(String email, RegisterRequest request, Organization department) {
        try {
            return userRepository.saveAndFlush(User.builder()
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
    }

    /**
     * A pending account whose newest verification link has run out is taken over by the new registration, so an
     * address is not lost to somebody who mistyped it or never opened the email. While a link is still usable
     * the address stays taken.
     */
    private User replaceAbandoned(User existing, RegisterRequest request, Organization department) {
        boolean abandoned = existing.getAccountStatus() == AccountStatus.PENDING
            && tokens.countUsable(existing, TokenPurpose.EMAIL_VERIFICATION) == 0;
        if (!abandoned) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "email-taken",
                "An account with this address already exists");
        }
        existing.setFirstName(request.firstName().trim());
        existing.setLastName(request.lastName().trim());
        existing.setPasswordHash(passwordEncoder.encode(request.password()));
        existing.setOrganization(department);
        tokens.invalidate(existing, TokenPurpose.EMAIL_VERIFICATION);
        audit.record(AuditAction.REGISTRATION_REPLACED, existing.getId(),
            Map.of("organizationId", department.getId()));
        return existing;
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
