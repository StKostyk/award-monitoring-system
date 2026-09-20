package ua.edu.chnu.awards.common.web;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ua.edu.chnu.awards.user.service.UserNotFoundException;

/**
 * Maps domain exceptions to Problem Details responses.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    static final String TYPE_PREFIX = "urn:awards:problem:";

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail userNotFound(UserNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Not found");
        return problem;
    }

    @ExceptionHandler(ApiProblemException.class)
    ProblemDetail apiProblem(ApiProblemException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        problem.setType(URI.create(TYPE_PREFIX + exception.getType()));
        problem.setTitle(exception.getStatus().getReasonPhrase());
        return problem;
    }
}
