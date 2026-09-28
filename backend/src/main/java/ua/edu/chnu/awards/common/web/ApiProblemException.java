package ua.edu.chnu.awards.common.web;

import java.util.Map;

import org.springframework.http.HttpStatus;

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
}
