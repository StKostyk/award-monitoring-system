package ua.edu.chnu.awards.delegation.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import ua.edu.chnu.awards.user.entity.RoleType;

/**
 * Request to lend an approval role to a colleague.
 *
 * @param delegateId     who receives the authority
 * @param role           the approval role lent
 * @param organizationId where the lent role applies
 * @param validFrom      first day the authority applies
 * @param validTo        last day the authority applies, at most 90 days after {@code validFrom}
 * @param reason         why the authority was handed over, shown to the delegate
 */
public record DelegationRequest(@NotNull Long delegateId, @NotNull RoleType role, @NotNull Long organizationId,
                                @NotNull LocalDate validFrom, @NotNull LocalDate validTo,
                                @Size(max = 500) String reason) {
}
