package ua.edu.chnu.awards.delegation.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;

import ua.edu.chnu.awards.authz.RoleType;

/**
 * Approval authority was taken back before its last day; the delegate is told, and the delegator too when
 * somebody else did it.
 *
 * @param recipients     the people to tell
 * @param role           the role that was lent
 * @param organization   English name of the organisation the role applied to
 * @param organizationUk Ukrainian name of the organisation, the English one when it has none
 * @param delegate       full name of the person who borrowed the authority
 * @param actor          full name of the person who took it back
 * @param validTo        last day the authority applies now
 * @param occurredAt     when it was taken back
 */
public record DelegationRevoked(List<Recipient> recipients, RoleType role, String organization,
                                String organizationUk, String delegate, String actor, LocalDate validTo,
                                Instant occurredAt) {

    public DelegationRevoked {
        recipients = List.copyOf(recipients);
    }

    /**
     * One person told about the revocation.
     *
     * @param userId     the person, null in events stored before it was carried
     * @param email      the address
     * @param otherParty full name of the other side of the delegation, null in events stored before it was carried
     */
    public record Recipient(Long userId, String email, String otherParty) {

        /**
         * Reads a recipient stored as a bare address.
         *
         * @param email the address
         * @return the recipient without id and other party
         */
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Recipient of(String email) {
            return new Recipient(null, email, null);
        }
    }
}
