package ua.edu.chnu.awards.award.controller;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fasterxml.jackson.databind.JsonMappingException;

import ua.edu.chnu.awards.common.web.FieldViolation;

/**
 * A form body the award endpoints cannot read (a date that does not exist, text where a number belongs) is
 * answered like any other refused field: 422 {@code validation-failed} naming the field.
 */
@RestControllerAdvice(assignableTypes = AwardController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AwardBodyProblems {

    static final String TYPE = "urn:awards:problem:validation-failed";

    /**
     * Turns an unreadable body into a field error.
     *
     * @param exception what the message converter reported
     * @return the problem naming the field, or the body as a whole when no field is known
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadable(HttpMessageNotReadableException exception) {
        String field = exception.getCause() instanceof JsonMappingException mapping
            ? mapping.getPath().stream().map(JsonMappingException.Reference::getFieldName)
                .filter(Objects::nonNull).collect(Collectors.joining("."))
            : "";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
            "The award form could not be read");
        problem.setType(URI.create(TYPE));
        problem.setTitle(HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase());
        problem.setProperty("errors", List.of(new FieldViolation(field.isEmpty() ? "body" : field, "invalid",
            "The value cannot be read")));
        return problem;
    }
}
