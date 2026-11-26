package ua.edu.chnu.awards.award.dto;

import java.time.Instant;

import ua.edu.chnu.awards.organization.dto.OrganizationRef;
import ua.edu.chnu.awards.user.entity.AccountStatus;

/**
 * A person without an account, or one who has claimed it since.
 *
 * @param id           the person
 * @param firstName    first name
 * @param lastName     last name
 * @param email        the address, masked in lists and duplicate matches, null when unknown
 * @param organization the department
 * @param status       {@code UNCLAIMED}, {@code PENDING} while a claim waits for verification, or the account's
 *                     status after the claim
 * @param addedBy      the faculty secretary or dean who added the person, null once that account is gone
 * @param invitedAt    when the last invitation went, null when none did
 * @param awardCount   how many awards the person has
 * @param version      the version a change must send
 */
public record Person(Long id, String firstName, String lastName, String email, OrganizationRef organization,
                     AccountStatus status, UserRef addedBy, Instant invitedAt, long awardCount, Long version) {
}
