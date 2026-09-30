package ua.edu.chnu.awards.award.dto;

import java.time.Instant;
import java.util.List;

import ua.edu.chnu.awards.award.entity.AwardSnapshot;
import ua.edu.chnu.awards.award.entity.VersionAction;

/**
 * One saved version of an award.
 *
 * @param number    the award's version number after the change
 * @param action    what produced the version
 * @param actor     who saved it, null for a baseline or an erased account
 * @param createdAt when it was saved
 * @param snapshot  the award's fields at this version
 * @param changes   fields that differ from the previous visible version, empty for the first
 */
public record AwardVersionResponse(long number, VersionAction action, UserRef actor, Instant createdAt,
                                   AwardSnapshot snapshot, List<FieldChange> changes) {
}
