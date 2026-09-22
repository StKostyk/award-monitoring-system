package ua.edu.chnu.awards.user.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.common.web.PageResponse;
import ua.edu.chnu.awards.user.dto.UserDetailResponse;
import ua.edu.chnu.awards.user.dto.UserDirectoryQuery;
import ua.edu.chnu.awards.user.dto.UserProfileResponse;
import ua.edu.chnu.awards.user.dto.UserSummaryResponse;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.service.UserDirectoryService;
import ua.edu.chnu.awards.user.service.UserProfileService;

import lombok.RequiredArgsConstructor;

/**
 * User endpoints: the caller's own profile and the directory for those who may read it.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private static final String CAN_READ = "@access.require('user:read:all', 'user:read:scope')";

    private final UserProfileService profileService;
    private final UserDirectoryService directoryService;

    /**
     * Profile of the caller.
     *
     * @param jwt the access token
     * @return profile
     */
    @GetMapping("/me")
    public UserProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return profileService.profileOf(Long.parseLong(jwt.getSubject()));
    }

    /**
     * Directory page, limited to the caller's scope unless they may read everybody.
     *
     * @param organization limit to an organisation and its descendants; must be inside the caller's scope
     * @param role         limit to holders of the role today
     * @param status       limit to an account status
     * @param unconfirmed  list only users who never held a role
     * @param q            name or address fragment
     * @param page         0-based page
     * @param size         page size, capped at 100
     * @return the page
     */
    @GetMapping
    @PreAuthorize(CAN_READ + " and (#organization == null or @access.canRead(#organization))")
    public PageResponse<UserSummaryResponse> list(@RequestParam(required = false) Long organization,
                                                  @RequestParam(required = false) RoleType role,
                                                  @RequestParam(required = false) AccountStatus status,
                                                  @RequestParam(defaultValue = "false") boolean unconfirmed,
                                                  @RequestParam(required = false) String q,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(directoryService.list(new UserDirectoryQuery(organization, role, status,
            unconfirmed, q), page, size));
    }

    /**
     * One user with the role history; unknown outside the caller's scope.
     *
     * @param id the user
     * @return the detail
     */
    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public UserDetailResponse detail(@PathVariable long id) {
        return directoryService.detail(id);
    }
}
