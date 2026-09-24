package ua.edu.chnu.awards.authz;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authorization.AuthorityAuthorizationDecision;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditLog;
import ua.edu.chnu.awards.audit.service.AuditService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * What happens when the API refuses a signed-in caller: every denial (request rules and method security alike)
 * is audited from Spring Security's authorization events, and the 403 body names what was missing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccessDenials {

    static final String TYPE = "urn:awards:problem:access-denied";
    static final String DEFAULT_DETAIL = "You are not allowed to do this";

    private final AuditService audit;

    /**
     * Audits a denial for a signed-in caller.
     *
     * @param event the denial published by Spring Security
     */
    @EventListener
    public void onDenied(AuthorizationDeniedEvent<?> event) {
        Authentication authentication = event.getAuthentication().get();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            return;
        }
        Map<String, Object> details = new HashMap<>();
        currentRequest().ifPresent(request -> {
            details.put("method", request.getMethod());
            details.put("path", request.getRequestURI());
            if (missing(request).isEmpty()
                && event.getAuthorizationResult() instanceof AuthorityAuthorizationDecision decision) {
                request.setAttribute(AccessScope.MISSING_ATTRIBUTE, "authority " + decision.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority).sorted().collect(Collectors.joining(" or "))
                    + " is required");
            }
            missing(request).ifPresent(missing -> details.put("required", missing));
        });
        try {
            audit.recordSeparately(AuditAction.ACCESS_DENIED, AuditLog.AUTHORIZATION, subjectOf(token), details);
        } catch (DataAccessException e) {
            log.error("Access denial of user {} on {} not audited: {}", subjectOf(token), details.get("path"),
                e.getMessage());
        }
    }

    private static Long subjectOf(JwtAuthenticationToken token) {
        try {
            return Long.valueOf(token.getToken().getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The 403 body for the current request.
     *
     * @param request the refused request
     * @return problem details naming the missing permission or scope when known
     */
    public ProblemDetail problem(HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
            missing(request).orElse(DEFAULT_DETAIL));
        problem.setType(URI.create(TYPE));
        problem.setTitle(HttpStatus.FORBIDDEN.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    private static Optional<String> missing(HttpServletRequest request) {
        return Optional.ofNullable(request.getAttribute(AccessScope.MISSING_ATTRIBUTE)).map(String::valueOf);
    }

    private static Optional<HttpServletRequest> currentRequest() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
            .filter(ServletRequestAttributes.class::isInstance)
            .map(attributes -> ((ServletRequestAttributes) attributes).getRequest());
    }
}
