package ua.edu.chnu.awards.award.service;

import ua.edu.chnu.awards.common.web.NotFoundException;

/**
 * The organisation is not a faculty or lies outside the caller's scope.
 */
public class FacultyNotFoundException extends NotFoundException {

    private static final long serialVersionUID = 1L;

    public FacultyNotFoundException(long organizationId) {
        super("Faculty " + organizationId + " not found");
    }
}
