package ua.edu.chnu.awards.gdpr;

import java.util.Collection;
import java.util.Set;

import ua.edu.chnu.awards.gdpr.dto.ConsentType;

/**
 * The current consent state of a person, for modules that act on an optional consent.
 */
public interface ConsentLookup {

    /**
     * Whether the person's newest record of a consent is a grant.
     *
     * @param userId the person
     * @param type   the consent
     * @return false when the person never chose or withdrew
     */
    boolean granted(long userId, ConsentType type);

    /**
     * The people among several whose newest record of a consent is a grant.
     *
     * @param userIds the people
     * @param type    the consent
     * @return ids of those who granted it
     */
    Set<Long> grantedAmong(Collection<Long> userIds, ConsentType type);
}
