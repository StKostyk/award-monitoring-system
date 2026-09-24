package ua.edu.chnu.awards.user.controller;

import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.security.AccessTokenDecoder;
import ua.edu.chnu.awards.auth.security.AccountStatusChecker;
import ua.edu.chnu.awards.auth.security.JpaUserDetailsService;
import ua.edu.chnu.awards.auth.security.JwtAuthorityConverter;
import ua.edu.chnu.awards.auth.security.LockedAccountChecker;
import ua.edu.chnu.awards.auth.security.LoginAccessDeniedHandler;
import ua.edu.chnu.awards.auth.security.LoginFailureHandler;
import ua.edu.chnu.awards.auth.security.ProblemDetailsEntryPoint;
import ua.edu.chnu.awards.auth.security.RolePermissions;
import ua.edu.chnu.awards.authz.AccessDenials;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.authz.OrganizationTree;
import ua.edu.chnu.awards.authz.ProblemDetailsAccessDeniedHandler;
import ua.edu.chnu.awards.authz.RoleLevels;
import ua.edu.chnu.awards.common.web.ApiExceptionHandler;
import ua.edu.chnu.awards.config.InfrastructureConfig;
import ua.edu.chnu.awards.config.LoginSessionConfig;
import ua.edu.chnu.awards.config.SecurityConfig;
import ua.edu.chnu.awards.user.service.RoleAssignmentService;
import ua.edu.chnu.awards.user.service.UserDirectoryService;
import ua.edu.chnu.awards.user.service.UserProfileService;

/**
 * The real security chain of {@code /api/v1/users} with every collaborator behind it mocked, so the slices
 * below only say what they assert.
 */
@Import({SecurityConfig.class, LoginSessionConfig.class, InfrastructureConfig.class, JwtAuthorityConverter.class,
    ProblemDetailsEntryPoint.class, LoginAccessDeniedHandler.class, ApiExceptionHandler.class, AccessScope.class,
    RoleLevels.class, RolePermissions.class, AccessDenials.class, ProblemDetailsAccessDeniedHandler.class})
abstract class AbstractUserEndpointsTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected UserProfileService profileService;

    @MockitoBean
    protected UserDirectoryService directoryService;

    @MockitoBean
    protected RoleAssignmentService roleAssignmentService;

    @MockitoBean
    protected OrganizationTree tree;

    @MockitoBean
    protected AuditService audit;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean(answers = Answers.RETURNS_MOCKS)
    private AccessTokenDecoder accessTokenDecoder;

    @MockitoBean
    private JpaUserDetailsService userDetailsService;

    @MockitoBean
    private AccountStatusChecker statusChecker;

    @MockitoBean
    private LoginFailureHandler failureHandler;

    @MockitoBean
    private LockedAccountChecker lockChecker;

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private StringRedisTemplate redisTemplate;
}
