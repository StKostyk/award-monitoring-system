package ua.edu.chnu.awards.award.service;

import ua.edu.chnu.awards.common.web.NotFoundException;

/**
 * The organisation is neither a faculty nor a department.
 */
public class UnitNotFoundException extends NotFoundException {

    private static final long serialVersionUID = 1L;

    public UnitNotFoundException(long organizationId) {
        super("Unit " + organizationId + " not found");
    }
}
