package ua.edu.chnu.awards.user;

import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;

/**
 * Takes back approval authority that rests on a role or a membership which has just changed; implemented by the
 * module that records delegations.
 */
public interface LentAuthority {

    /**
     * Takes back what a role had lent, and what its holder had borrowed for an organisation they no longer
     * belong to, because the role itself has just been taken back.
     *
     * @param actor      who took the role back
     * @param assignment the role assignment that has just ended
     */
    void revokeForRole(User actor, UserRole assignment);

    /**
     * Takes back authority a person borrowed for organisations in which they no longer hold any role, because
     * they have just been moved to another department.
     *
     * @param actor    who moved the person
     * @param delegate the person moved, with their new role already saved
     */
    void revokeForMove(User actor, User delegate);
}
