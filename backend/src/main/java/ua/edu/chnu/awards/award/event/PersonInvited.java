package ua.edu.chnu.awards.award.event;

/**
 * A faculty secretary or dean added a person without an account, or invited one again; the invitation to register
 * goes to the person once the surrounding transaction commits.
 *
 * @param personId              the person
 * @param email                 the person's address
 * @param firstName             used in the greeting
 * @param addedBy               full name of the faculty secretary or dean who sent the invitation
 * @param department            the department in English, the Ukrainian name when it has none
 * @param departmentUk          the department in Ukrainian, the English name when it has none
 * @param dataProtectionContact the address of the data protection officer
 */
public record PersonInvited(long personId, String email, String firstName, String addedBy, String department,
                            String departmentUk, String dataProtectionContact) {
}
