package ua.edu.chnu.awards.delegation.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;

import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

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
import ua.edu.chnu.awards.delegation.service.DelegationService;

/**
 * The real security chain of {@code /api/v1/delegations} with every collaborator behind it mocked, so the
 * slice below only says what it asserts.
 */
@Import({SecurityConfig.class, LoginSessionConfig.class, InfrastructureConfig.class, JwtAuthorityConverter.class,
    ProblemDetailsEntryPoint.class, LoginAccessDeniedHandler.class, ApiExceptionHandler.class, AccessScope.class,
    RoleLevels.class, RolePermissions.class, AccessDenials.class, ProblemDetailsAccessDeniedHandler.class,
    DelegationStateConverter.class})
abstract class AbstractDelegationEndpointsTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected DelegationService delegationService;

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

    /**
     * A dean of faculty 9, the caller of most of these requests.
     *
     * @return the bearer token to send
     */
    protected static RequestPostProcessor approver() {
        return jwt().jwt(token -> token.subject("7").claim("role_scopes", List.of("DEAN:9")))
            .authorities(new SimpleGrantedAuthority("award:approve:level1"),
                new SimpleGrantedAuthority("award:approve:level2"));
    }
}
