package ua.edu.chnu.awards.award.service;

import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.user.entity.User;

/**
 * Who is told about a review of an award: its owner, or the request's submitter when the owner has no account of
 * their own yet.
 */
public final class NoticeRecipientHelper {

    private NoticeRecipientHelper() {
    }

    /**
     * The account told about a decision or correction.
     *
     * @param request the request of the award
     * @return the owner, or the submitter for an owner without an account
     */
    public static User recipient(AwardRequest request) {
        User owner = request.getAward().getOwner();
        return owner.isWithoutAccount() ? request.getSubmitter() : owner;
    }

    /**
     * The name the notice uses for an owner without an account.
     *
     * @param award the award
     * @return surname and first name of an owner without an account, null otherwise
     */
    public static String person(Award award) {
        User owner = award.getOwner();
        return owner.isWithoutAccount() ? owner.getLastName() + " " + owner.getFirstName() : null;
    }
}
