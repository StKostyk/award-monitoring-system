package ua.edu.chnu.awards.delegation.service;

/**
 * The referenced delegation does not exist.
 */
public class DelegationNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DelegationNotFoundException(long delegationId) {
        super("Delegation " + delegationId + " not found");
    }
}
