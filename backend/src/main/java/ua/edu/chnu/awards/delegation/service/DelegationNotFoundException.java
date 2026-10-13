package ua.edu.chnu.awards.delegation.service;

import ua.edu.chnu.awards.common.web.NotFoundException;

/**
 * The referenced delegation does not exist.
 */
public class DelegationNotFoundException extends NotFoundException {

    private static final long serialVersionUID = 1L;

    public DelegationNotFoundException(long delegationId) {
        super("Delegation " + delegationId + " not found");
    }
}
