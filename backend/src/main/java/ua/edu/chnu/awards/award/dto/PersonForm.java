package ua.edu.chnu.awards.award.dto;

/**
 * The form of a person without an account as sent on add and change; {@code version} is required on change and
 * ignored on add, {@code duplicateAcknowledged} is read on add only.
 *
 * @param firstName             first name, 1 to 100 characters after trimming
 * @param lastName              last name, 1 to 100 characters after trimming
 * @param organizationId        a department inside the caller's faculty secretary or dean scope
 * @param email                 the institutional address, null when unknown
 * @param duplicateAcknowledged true to store the person although one of the same name exists in the department
 * @param version               the version the change is based on
 */
public record PersonForm(String firstName, String lastName, Long organizationId, String email,
                         Boolean duplicateAcknowledged, Long version) {

    /**
     * Whether the caller confirmed that the person differs from those of the same name.
     *
     * @return true only for an explicit true
     */
    public boolean acknowledged() {
        return Boolean.TRUE.equals(duplicateAcknowledged);
    }
}
