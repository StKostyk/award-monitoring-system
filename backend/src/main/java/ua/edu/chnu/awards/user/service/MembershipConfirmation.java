package ua.edu.chnu.awards.user.service;

import java.util.Optional;

import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;

/**
 * Decides whether a self-registered account's membership of the department it picked can be confirmed without a
 * person. An institutional address proves employment at the university, not membership of one department, so
 * the answer is the seam where a future directory lookup (LDAP, HR) would replace the manual step.
 */
public interface MembershipConfirmation {

    /**
     * The first role of a freshly registered account, when membership is proven without asking anybody.
     *
     * @param user       the freshly registered account
     * @param department the department the person picked
     * @return the role to grant, empty when a person has to confirm the membership
     */
    Optional<UserRole> confirm(User user, Organization department);
}
