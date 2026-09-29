package ua.edu.chnu.awards.common.web;

import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * A path or query value of the wrong type (an id that is not a number or does not fit 64 bits) is answered as
 * 400 {@code invalid-parameter} naming the parameter. Ordered before the framework's own problem details, which
 * would answer the same status without a type.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InvalidParameterProblems {

    /**
     * Turns a value that cannot be converted into a typed problem.
     *
     * @param exception what the argument resolver reported
     * @return the problem naming the parameter
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail invalidParameter(MethodArgumentTypeMismatchException exception) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-parameter",
            "The value of " + exception.getName() + " is not valid", Map.of("parameter", exception.getName()))
            .toProblem();
    }
}
