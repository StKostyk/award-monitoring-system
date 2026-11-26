package ua.edu.chnu.awards.audit;

import java.util.Collection;
import java.util.Map;

/**
 * Names the people who appear as actors in the audit trail; implemented by the module that owns the accounts.
 */
public interface ActorDirectory {

    /**
     * The actors with the given ids; ids without an account are left out.
     *
     * @param userIds the actor ids
     * @return the actors by id
     */
    Map<Long, Actor> actorsOf(Collection<Long> userIds);

    /**
     * An actor as the audit trail shows it.
     *
     * @param fullName     the actor's name
     * @param emailAddress the actor's address
     */
    record Actor(String fullName, String emailAddress) {
    }
}
