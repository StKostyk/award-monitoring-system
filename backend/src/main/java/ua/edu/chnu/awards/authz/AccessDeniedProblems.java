package ua.edu.chnu.awards.authz;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.RequiredArgsConstructor;

/**
 * Renders a method-security refusal of a signed-in caller as the typed 403 body; anonymous callers are left to the
 * authentication entry point.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class AccessDeniedProblems {

    private final AccessDenials denials;

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accessDenied(HttpServletRequest request, AccessDeniedException exception) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw exception;
        }
        return denials.problem(request);
    }
}
