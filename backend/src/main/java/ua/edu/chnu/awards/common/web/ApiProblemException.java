package ua.edu.chnu.awards.common.web;

import org.springframework.http.HttpStatus;

/**
 * A request that cannot be fulfilled for a business reason; rendered as a Problem Details response.
 */
public class ApiProblemException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String type;

    public ApiProblemException(HttpStatus status, String type, String detail) {
        this(status, type, detail, null);
    }

    public ApiProblemException(HttpStatus status, String type, String detail, Throwable cause) {
        super(detail, cause);
        this.status = status;
        this.type = type;
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
}
