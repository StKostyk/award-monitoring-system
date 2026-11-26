package ua.edu.chnu.awards.user.event;

import java.time.Instant;
import java.time.LocalDate;

import ua.edu.chnu.awards.authz.RoleType;

/**
 * A role was taken back; the former holder is told once the surrounding transaction commits.
 *
 * @param userId         the former holder, null in events stored before it was carried
 * @param email          recipient
 * @param firstName      used in the greeting
 * @param role           the role taken back
 * @param organization   English name of the organisation the role applied to
 * @param organizationUk Ukrainian name of the organisation, the English one when it has none
 * @param actor          full name of the person who took it back
 * @param lastDay        last day the role applies
 * @param occurredAt     when it was taken back
 */
public record RoleRevoked(Long userId, String email, String firstName, RoleType role, String organization,
                          String organizationUk, String actor, LocalDate lastDay, Instant occurredAt) {
}
