package ua.edu.chnu.awards.common.web;

import org.springframework.http.HttpStatus;

/**
 * A resource that does not exist or lies outside the caller's scope; rendered as a 404 Problem Details response.
 */
public class NotFoundException extends ApiProblemException {

    private static final long serialVersionUID = 1L;

    protected NotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, "not-found", detail);
    }
}
