package ua.edu.chnu.awards.common.web;

import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * A path or query value of the wrong type (an id that is not a number or does not fit 64 bits) is answered as
 * 400 {@code invalid-parameter} naming the parameter, a missing parameter or multipart part as 400
 * {@code missing-parameter}, and an upload over the multipart limits as 413 {@code file-too-large}. Ordered
 * before the framework's own problem details, which would answer the same status without a type.
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

    /**
     * A required request parameter was not sent.
     *
     * @param exception what the argument resolver reported
     * @return the problem naming the parameter
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail missingParameter(MissingServletRequestParameterException exception) {
        return missing(exception.getParameterName());
    }

    /**
     * A required multipart part was not sent.
     *
     * @param exception what the argument resolver reported
     * @return the problem naming the part
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ProblemDetail missingPart(MissingServletRequestPartException exception) {
        return missing(exception.getRequestPartName());
    }

    /**
     * A multipart request whose file or body is over the configured limits; nothing of it was handled.
     *
     * @param exception what the multipart resolver reported
     * @return 413 {@code file-too-large}
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail uploadTooLarge(MaxUploadSizeExceededException exception) {
        return new ApiProblemException(HttpStatus.PAYLOAD_TOO_LARGE, "file-too-large",
            "The file is larger than the limit").toProblem();
    }

    private static ProblemDetail missing(String name) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, "missing-parameter", name + " is required",
            Map.of("parameter", name)).toProblem();
    }
}
