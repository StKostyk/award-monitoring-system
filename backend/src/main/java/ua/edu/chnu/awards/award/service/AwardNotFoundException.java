package ua.edu.chnu.awards.award.service;

/**
 * The award does not exist or the caller may not see it.
 */
public class AwardNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AwardNotFoundException(long awardId) {
        super("Award " + awardId + " not found");
    }
}
