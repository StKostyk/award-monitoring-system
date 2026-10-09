package ua.edu.chnu.awards.award.service;

/**
 * The organisation is neither a faculty nor a department.
 */
public class UnitNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnitNotFoundException(long organizationId) {
        super("Unit " + organizationId + " not found");
    }
}
