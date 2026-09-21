package ua.edu.chnu.awards.auth.security;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.service.LoginAttemptService;
import ua.edu.chnu.awards.common.EmailUtils;
import ua.edu.chnu.awards.common.web.ClientRequest;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

/**
 * Records a failed login (audit trail, failure counter) and sends the user back to the login page with an
 * error code the page can translate. A locked address answers {@code LOCKED} whether or not an account exists
 * behind it, and the typed address is kept in the audit row only when it belongs to an account.
 */
@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    static final String BAD_CREDENTIALS = "BAD_CREDENTIALS";
    static final String LOCKED = "LOCKED";

    private final LoginAttemptService attempts;
    private final AuditService audit;
    private final UserRepository userRepository;

    public LoginFailureHandler(LoginAttemptService attempts, AuditService audit, UserRepository userRepository) {
        this.attempts = attempts;
        this.audit = audit;
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String code = codeOf(exception);
        String email = EmailUtils.normalize(request.getParameter("username"));
        User user = userRepository.findByEmailAddressIgnoreCase(email).orElse(null);
        if (BAD_CREDENTIALS.equals(code)
            && (attempts.isLocked(email) || attempts.recordFailure(email, ClientRequest.from(request).ip()))) {
            code = LOCKED;
        }
        Map<String, Object> details = new HashMap<>();
        details.put("reason", code);
        if (user != null) {
            details.put("email", user.getEmailAddress());
        }
        audit.record(AuditAction.LOGIN_FAILED, user == null ? null : user.getId(), details);
        getRedirectStrategy().sendRedirect(request, response, "/login?error=" + code);
    }

    private static String codeOf(AuthenticationException exception) {
        if (exception instanceof AccountStatusRefusedException refused) {
            return refused.getStatus().name();
        }
        return exception instanceof LockedException ? LOCKED : BAD_CREDENTIALS;
    }
}
