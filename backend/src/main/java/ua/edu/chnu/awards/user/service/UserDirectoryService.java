package ua.edu.chnu.awards.user.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.user.dto.UserDetailResponse;
import ua.edu.chnu.awards.user.dto.UserDirectoryQuery;
import ua.edu.chnu.awards.user.dto.UserSummaryResponse;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.mapper.UserProfileMapper;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;
import ua.edu.chnu.awards.user.repository.UserSpecifications;

import lombok.RequiredArgsConstructor;

/**
 * The user directory as seen by the caller: everybody for {@code user:read:all}, otherwise the members of the
 * organisations inside the caller's role scopes. Unverified accounts never appear.
 */
@Service
@RequiredArgsConstructor
public class UserDirectoryService {

    public static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserProfileMapper mapper;
    private final UserSpecifications specifications;
    private final AccessScope access;
    private final Clock clock;

    /**
     * One page of the directory.
     *
     * @param query filters
     * @param page  0-based page number
     * @param size  requested page size, capped at {@link #MAX_PAGE_SIZE}
     * @return the page
     */
    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> list(UserDirectoryQuery query, int page, int size) {
        LocalDate today = LocalDate.now(clock);
        Specification<User> specification = Specification.allOf(
            scope(query.organizationId()),
            specifications.withStatus(query.status()),
            query.role() == null ? null : specifications.holdingRole(query.role(), today),
            query.unconfirmed() ? specifications.neverConfirmed() : null,
            specifications.matching(query.q()));
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
            Sort.by("lastName", "firstName", "id"));
        Page<User> users = userRepository.findAll(specification, request);
        List<Long> ids = users.map(User::getId).toList();
        Map<Long, List<UserRole>> roles = ids.isEmpty() ? Map.of()
            : userRoleRepository.findCurrentByUserIds(ids, today).stream()
                .collect(Collectors.groupingBy(role -> role.getUser().getId()));
        Set<Long> confirmed = ids.isEmpty() ? Set.of() : userRoleRepository.findEverAssignedUserIds(ids);
        return users.map(user -> mapper.toSummary(user, roles.getOrDefault(user.getId(), List.of()),
            confirmed.contains(user.getId())));
    }

    /**
     * One user with the role history; a user outside the caller's scope is reported as unknown.
     *
     * @param userId the user
     * @return the detail
     * @throws UserNotFoundException when unknown, unverified or outside the scope
     */
    @Transactional(readOnly = true)
    public UserDetailResponse detail(long userId) {
        User user = userRepository.findById(userId)
            .filter(found -> found.getAccountStatus() != AccountStatus.PENDING)
            .filter(found -> access.readableOrganizations()
                .map(ids -> ids.contains(found.getOrganization().getId())).orElse(true))
            .orElseThrow(() -> new UserNotFoundException(userId));
        List<UserRole> history = userRoleRepository.findHistoryByUserId(userId);
        LocalDate today = LocalDate.now(clock);
        List<UserRole> current = history.stream()
            .filter(role -> !role.getValidFrom().isAfter(today))
            .filter(role -> role.getValidTo() == null || !role.getValidTo().isBefore(today))
            .toList();
        return mapper.toDetail(user, current, history);
    }

    private Specification<User> scope(Long organizationId) {
        Optional<Set<Long>> readable = access.readableOrganizations();
        if (organizationId == null) {
            return readable.map(specifications::inOrganizations).orElse(null);
        }
        Set<Long> requested = access.subtreeOf(organizationId);
        Set<Long> visible = readable.map(ids -> ids.stream().filter(requested::contains)
            .collect(Collectors.toSet())).orElse(requested);
        return specifications.inOrganizations(visible);
    }
}
