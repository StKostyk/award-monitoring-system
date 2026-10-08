package ua.edu.chnu.awards.award.service;

/**
 * The organisation is not a faculty or lies outside the caller's scope.
 */
public class FacultyNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FacultyNotFoundException(long organizationId) {
        super("Faculty " + organizationId + " not found");
    }
}
