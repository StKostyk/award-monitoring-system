package ua.edu.chnu.awards.award.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ua.edu.chnu.awards.award.service.ReviewPeriods;

/**
 * A review period body that cannot be read (text where the number of days belongs) is refused like a number out
 * of range: 422 {@code validation-failed} on {@code workingDays}.
 */
@RestControllerAdvice(assignableTypes = ReviewPeriodController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReviewPeriodBodyProblems {

    /**
     * Turns an unreadable body into the range error.
     *
     * @param exception what the message converter reported
     * @return the problem naming {@code workingDays}
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadable(HttpMessageNotReadableException exception) {
        return ReviewPeriods.rangeViolation().toProblem();
    }
}
