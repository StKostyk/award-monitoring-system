package ua.edu.chnu.awards.award.service;

import ua.edu.chnu.awards.common.web.NotFoundException;

/**
 * The person does not exist, was never added by a faculty secretary or dean, or lies outside the caller's scope.
 */
public class PersonNotFoundException extends NotFoundException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param id the person
     */
    public PersonNotFoundException(long id) {
        super("Person " + id + " not found");
    }
}
