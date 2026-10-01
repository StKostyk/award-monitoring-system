package ua.edu.chnu.awards.award.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.time.Instant;
import java.time.LocalDate;
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
import ua.edu.chnu.awards.audit.service.AuditTrailService;
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
import ua.edu.chnu.awards.award.dto.AwardResponse;
import ua.edu.chnu.awards.award.dto.CategoryRef;
import ua.edu.chnu.awards.award.dto.RequestSummary;
import ua.edu.chnu.awards.award.dto.UserRef;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.service.AwardHistory;
import ua.edu.chnu.awards.award.service.AwardService;
import ua.edu.chnu.awards.award.service.AwardSubmission;
import ua.edu.chnu.awards.common.web.ApiExceptionHandler;
import ua.edu.chnu.awards.config.InfrastructureConfig;
import ua.edu.chnu.awards.config.LoginSessionConfig;
import ua.edu.chnu.awards.config.SecurityConfig;
import ua.edu.chnu.awards.user.dto.OrganizationRef;
import ua.edu.chnu.awards.user.entity.OrganizationType;

/**
 * The real security chain of {@code /api/v1/awards} with the services behind it mocked.
 */
@Import({SecurityConfig.class, LoginSessionConfig.class, InfrastructureConfig.class, JwtAuthorityConverter.class,
    ProblemDetailsEntryPoint.class, LoginAccessDeniedHandler.class, ApiExceptionHandler.class, AccessScope.class,
    RoleLevels.class, RolePermissions.class, AccessDenials.class, ProblemDetailsAccessDeniedHandler.class,
    AwardBodyProblems.class})
abstract class AbstractAwardEndpointsTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected AwardService awardService;

    @MockitoBean
    protected AwardSubmission submission;

    @MockitoBean
    protected AuditService audit;

    @MockitoBean
    protected AwardHistory history;

    @MockitoBean
    protected AuditTrailService auditTrail;

    @MockitoBean
    private OrganizationTree tree;

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
     * An employee of department 64 with the permissions of the role.
     *
     * @return the bearer token to send
     */
    protected static RequestPostProcessor employee() {
        return jwt().jwt(token -> token.subject("21").claim("role_scopes", List.of("EMPLOYEE:64")))
            .authorities(new SimpleGrantedAuthority("award:read:own"), new SimpleGrantedAuthority("award:create"),
                new SimpleGrantedAuthority("award:update:own"));
    }

    /**
     * The system administrator, who reads every award and submits none.
     *
     * @return the bearer token to send
     */
    protected static RequestPostProcessor administrator() {
        return jwt().jwt(token -> token.subject("1").claim("role_scopes", List.of("SYSTEM_ADMIN:1")))
            .authorities(new SimpleGrantedAuthority("award:read:own"), new SimpleGrantedAuthority("award:read:all"));
    }

    /**
     * An award as the service returns it.
     *
     * @param status the award status
     * @return the response
     */
    protected static AwardResponse award(AwardStatus status) {
        RequestSummary request = status == AwardStatus.DRAFT ? null
            : new RequestSummary(RequestStatus.SUBMITTED, ApprovalLevel.FACULTY_SECRETARY,
                Instant.parse("2026-09-28T09:00:00Z"), Instant.parse("2026-10-01T09:00:00Z"),
                LocalDate.of(2026, 10, 7), false);
        return new AwardResponse(5L, null, "Грамота МОН", null, null,
            new CategoryRef(13L, "Ministry Recognition", "Відзнака міністерства", RecognitionLevel.NATIONAL),
            "МОН України", LocalDate.of(2025, 5, 1), null, status, request == null ? null : 80,
            new UserRef(21L, "Анастасія Коваль", "employee.fmi@chnu.edu.ua"),
            new OrganizationRef(64L, "Algebra and Informatics", "Кафедра алгебри та інформатики", "DAI",
                OrganizationType.DEPARTMENT), request, List.of(), Instant.parse("2026-09-28T08:00:00Z"),
            Instant.parse("2026-09-28T08:30:00Z"), 3L);
    }
}
