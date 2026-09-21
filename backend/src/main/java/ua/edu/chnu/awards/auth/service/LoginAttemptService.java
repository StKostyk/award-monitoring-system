package ua.edu.chnu.awards.auth.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.event.AccountLocked;
import ua.edu.chnu.awards.common.EmailUtils;
import ua.edu.chnu.awards.config.ProtectionProperties;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Counts failed logins per typed address in Redis and locks the address when the limit is reached. Unknown
 * addresses are locked the same way so the lock message reveals nothing; only an existing account is audited
 * and reported to the administrators. Without Redis nothing is counted and nobody is locked; the outage is
 * logged.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

    static final String FAILURE_KEY_PREFIX = "auth:fail:";
    static final String LOCK_KEY_PREFIX = "auth:lock:";

    private final StringRedisTemplate redis;
    private final ProtectionProperties properties;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * Whether the address currently refuses logins.
     *
     * @param email the address typed at the login page
     * @return true while a lock is in force
     */
    public boolean isLocked(String email) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(LOCK_KEY_PREFIX + EmailUtils.normalize(email)));
        } catch (DataAccessException e) {
            log.error("Redis unavailable; lock state unknown, allowing the attempt: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Records a failed password check; the attempt that reaches the limit locks the address.
     *
     * @param email the address typed at the login page
     * @param ip    client address
     * @return true when this failure locked the address
     */
    public boolean recordFailure(String email, String ip) {
        String normalized = EmailUtils.normalize(email);
        String failureKey = FAILURE_KEY_PREFIX + normalized;
        try {
            Long failures = redis.opsForValue().increment(failureKey);
            if (failures != null && failures == 1) {
                redis.expire(failureKey, properties.failureWindow());
            }
            if (failures == null || failures != properties.maxFailures()) {
                return false;
            }
            redis.opsForValue().set(LOCK_KEY_PREFIX + normalized, "1", properties.lockDuration());
            redis.delete(failureKey);
            userRepository.findByEmailAddressIgnoreCase(normalized).ifPresent(user -> report(user, ip));
            return true;
        } catch (DataAccessException e) {
            log.error("Redis unavailable; failed login not counted: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Forgets the failures of an address after a successful login.
     *
     * @param email the address
     */
    public void reset(String email) {
        try {
            redis.delete(FAILURE_KEY_PREFIX + EmailUtils.normalize(email));
        } catch (DataAccessException e) {
            log.error("Redis unavailable; failure counter not reset: {}", e.getMessage());
        }
    }

    private void report(User user, String ip) {
        audit.record(AuditAction.ACCOUNT_LOCKED, user.getId(),
            Map.of("email", user.getEmailAddress(), "ip", String.valueOf(ip)));
        List<String> admins = userRoleRepository.findCurrentEmailsByRole(RoleType.SYSTEM_ADMIN, LocalDate.now(clock));
        events.publishEvent(new AccountLocked(user.getEmailAddress(), ip, clock.instant(),
            properties.lockDuration(), admins));
    }
}
