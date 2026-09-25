package ua.edu.chnu.awards.delegation.dto;

import java.time.Instant;
import java.time.LocalDate;

import ua.edu.chnu.awards.delegation.entity.DelegationState;
import ua.edu.chnu.awards.user.dto.OrganizationRef;
import ua.edu.chnu.awards.user.entity.RoleType;

/**
 * One delegation of approval authority.
 *
 * @param id           delegation identifier
 * @param role         the role lent
 * @param organization where the lent role applies
 * @param delegator    who lent the authority
 * @param delegate     who borrowed it
 * @param validFrom    first day it applies
 * @param validTo      last day it applies
 * @param reason       why it was handed over, null when none was given
 * @param state        where it stands today
 * @param createdAt    when it was created
 * @param revokedAt    when it was taken back, null while it stands
 */
public record DelegationResponse(Long id, RoleType role, OrganizationRef organization, UserBrief delegator,
                                 UserBrief delegate, LocalDate validFrom, LocalDate validTo, String reason,
                                 DelegationState state, Instant createdAt, Instant revokedAt) {
}
