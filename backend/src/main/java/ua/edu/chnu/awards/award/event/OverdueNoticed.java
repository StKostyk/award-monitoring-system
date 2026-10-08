package ua.edu.chnu.awards.award.event;

import java.time.Instant;
import java.util.List;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;

/**
 * Overdue requests one reviewer of the next level hears about in one run of the overdue job, e-mailed once the
 * marks commit.
 *
 * @param email     the reviewer's address
 * @param name      the reviewer's full name
 * @param requests  the requests, oldest deadline first
 */
public record OverdueNoticed(String email, String name, List<Item> requests) {

    public OverdueNoticed {
        requests = List.copyOf(requests);
    }

    /**
     * One overdue request in the digest.
     *
     * @param awardId  the award
     * @param title    the English title, the Ukrainian one when it has none
     * @param titleUk  the Ukrainian title, the English one when it has none
     * @param owner    the owner's full name
     * @param level    the level the request waits at
     * @param deadline when the review period ended
     * @param reviewer who holds the request, null when nobody took it
     */
    public record Item(long awardId, String title, String titleUk, String owner, ApprovalLevel level,
                       Instant deadline, String reviewer) {
    }
}
