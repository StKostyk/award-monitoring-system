package ua.edu.chnu.awards.award.event;

import java.time.Instant;

/**
 * A reviewer took a request over from the colleague holding it; the previous holder is told once the surrounding
 * transaction commits.
 *
 * @param userId     the previous holder, null in events stored before it was carried
 * @param email      the previous holder's address
 * @param firstName  used in the greeting
 * @param awardId    the award
 * @param title      the award's title, the Ukrainian one when it has no English title
 * @param titleUk    the award's Ukrainian title, the English one when it has none
 * @param reviewer   full name of the reviewer who took it over
 * @param occurredAt when it was taken over
 */
public record ReviewTakenOver(Long userId, String email, String firstName, long awardId, String title,
                              String titleUk, String reviewer, Instant occurredAt) {
}
