package ua.edu.chnu.awards.common.web;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ua.edu.chnu.awards.authz.AccessDenials;
import ua.edu.chnu.awards.user.service.UserNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Maps domain exceptions to Problem Details responses.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class ApiExceptionHandler {

    static final String TYPE_PREFIX = "urn:awards:problem:";

    private final AccessDenials denials;

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail userNotFound(UserNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Not found");
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accessDenied(HttpServletRequest request, AccessDeniedException exception) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw exception;
        }
        return denials.problem(request);
    }

    @ExceptionHandler(ApiProblemException.class)
    ProblemDetail apiProblem(ApiProblemException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        problem.setType(URI.create(TYPE_PREFIX + exception.getType()));
        problem.setTitle(exception.getStatus().getReasonPhrase());
        return problem;
    }
}
