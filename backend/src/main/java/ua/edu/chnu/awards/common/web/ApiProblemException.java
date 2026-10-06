package ua.edu.chnu.awards.common.web;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * A request that cannot be fulfilled for a business reason; rendered as a Problem Details response.
 */
public class ApiProblemException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String type;
    private final transient Map<String, Object> properties;

    public ApiProblemException(HttpStatus status, String type, String detail) {
        this(status, type, detail, (Throwable) null);
    }

    public ApiProblemException(HttpStatus status, String type, String detail, Throwable cause) {
        super(detail, cause);
        this.status = status;
        this.type = type;
        this.properties = Map.of();
    }

    public ApiProblemException(HttpStatus status, String type, String detail, Map<String, Object> properties) {
        super(detail);
        this.status = status;
        this.type = type;
        this.properties = Map.copyOf(properties);
    }

    /**
     * A form with refused fields: 422 {@code validation-failed} listing them under {@code errors}.
     *
     * @param detail what was refused, for the problem detail
     * @param errors the refused fields
     * @return the exception
     */
    public static ApiProblemException validationFailed(String detail, List<FieldViolation> errors) {
        return new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "validation-failed", detail,
            Map.of("errors", List.copyOf(errors)));
    }

    /**
     * A request refused by a rate limit or throttle: 429 {@code too-many-requests} with {@code retryAfter}, which
     * is also sent as the {@code Retry-After} header.
     *
     * @param detail            why the request was refused, for the problem detail
     * @param retryAfterSeconds when the caller may try again, at least 1
     * @return the exception
     */
    public static ApiProblemException tooManyRequests(String detail, long retryAfterSeconds) {
        return new ApiProblemException(HttpStatus.TOO_MANY_REQUESTS, "too-many-requests", detail,
            Map.of(ApiExceptionHandler.RETRY_AFTER, Math.max(1, retryAfterSeconds)));
    }

    public HttpStatus getStatus() {
        return status;
    }

    /**
     * Short problem type slug, rendered as {@code urn:awards:problem:<type>}.
     *
     * @return the slug
     */
    public String getType() {
        return type;
    }

    /**
     * Extra members of the problem body, such as the field errors of a refused form.
     *
     * @return the members, empty when there are none
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Renders the problem as a Problem Details body.
     *
     * @return the problem with status, typed URN, reason phrase as title and the extra members
     */
    public ProblemDetail toProblem() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, getMessage());
        problem.setType(URI.create(ApiExceptionHandler.TYPE_PREFIX + type));
        problem.setTitle(status.getReasonPhrase());
        properties.forEach(problem::setProperty);
        return problem;
    }
}
