package ua.edu.chnu.awards.user.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.user.dto.UserProfileResponse;
import ua.edu.chnu.awards.user.dto.UserUpdateRequest;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.mapper.UserProfileMapper;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Reads user profiles and lets users correct their own names.
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    /** Entity type of the audit rows about a person's own account. */
    public static final String AUDIT_ENTITY = "USER";
    private static final int WRITE_ATTEMPTS = 2;

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserProfileMapper mapper;
    private final Clock clock;
    private final ProfileNameRules nameRules;
    private final AuditService audit;
    private final TransactionTemplate transactions;

    /**
     * Profile of the user identified by the token subject.
     *
     * @param userId the user id
     * @return profile with the roles in effect today
     */
    @Transactional(readOnly = true)
    public UserProfileResponse profileOf(long userId) {
        return toProfile(load(userId));
    }

    /**
     * Stores the sent names of the caller and audits the fields that actually changed. A write that collides
     * with a concurrent change of the same row is repeated once on the fresh row.
     *
     * @param userId  the caller
     * @param request the names to set
     * @return the profile after the change
     * @throws ApiProblemException 422 {@code validation-failed} for refused fields, 409 {@code concurrent-update}
     *                             when the row changed twice under the write
     */
    public UserProfileResponse update(long userId, UserUpdateRequest request) {
        Map<String, String> names = nameRules.check(request);
        OptimisticLockingFailureException conflict = null;
        for (int attempt = 0; attempt < WRITE_ATTEMPTS; attempt++) {
            try {
                return transactions.execute(status -> apply(userId, names));
            } catch (OptimisticLockingFailureException e) {
                conflict = e;
            }
        }
        throw new ApiProblemException(HttpStatus.CONFLICT, "concurrent-update",
            "The profile was changed at the same time; reload and try again", conflict);
    }

    private UserProfileResponse apply(long userId, Map<String, String> names) {
        User user = load(userId);
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        names.forEach((column, name) -> {
            String current = "first_name".equals(column) ? user.getFirstName() : user.getLastName();
            if (!name.equals(current)) {
                before.put(column, current);
                after.put(column, name);
            }
        });
        if (!after.isEmpty()) {
            user.setFirstName(names.getOrDefault("first_name", user.getFirstName()));
            user.setLastName(names.getOrDefault("last_name", user.getLastName()));
            userRepository.saveAndFlush(user);
            audit.recordChange(AuditAction.PROFILE_UPDATED, AUDIT_ENTITY, userId, userId, before, after);
        }
        return toProfile(user);
    }

    private User load(long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    private UserProfileResponse toProfile(User user) {
        List<UserRole> roles = userRoleRepository.findCurrentByUserId(user.getId(), LocalDate.now(clock));
        return mapper.toProfile(user, roles, userRoleRepository.existsByUserId(user.getId()));
    }
}
