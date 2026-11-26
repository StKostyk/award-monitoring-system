package ua.edu.chnu.awards.user.service;

/**
 * Counts the awards that were entered for a person before the person had an account, so the account module can
 * greet a claimed account without depending on the award module.
 */
public interface WaitingAwards {

    /**
     * The number of awards the user owns, in every status.
     *
     * @param userId the user
     * @return the award count
     */
    long count(Long userId);
}
