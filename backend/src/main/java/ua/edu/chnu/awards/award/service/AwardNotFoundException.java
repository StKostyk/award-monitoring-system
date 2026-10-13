package ua.edu.chnu.awards.award.service;

import ua.edu.chnu.awards.common.web.NotFoundException;

/**
 * The award does not exist or the caller may not see it.
 */
public class AwardNotFoundException extends NotFoundException {

    private static final long serialVersionUID = 1L;

    public AwardNotFoundException(long awardId) {
        super("Award " + awardId + " not found");
    }
}
