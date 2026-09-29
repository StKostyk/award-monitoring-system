package ua.edu.chnu.awards.user.dto;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Changes of the caller's own profile; an absent or null field stays as it is. Every other property of the body
 * is collected so it can be refused instead of silently ignored.
 */
@NoArgsConstructor
public class UserUpdateRequest {

    @Getter
    @Setter
    private String firstName;

    @Getter
    @Setter
    private String lastName;

    private final SortedSet<String> unknownProperties = new TreeSet<>();

    /**
     * Creates a request with the given names.
     *
     * @param firstName new first name, null to keep it
     * @param lastName  new last name, null to keep it
     */
    public UserUpdateRequest(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    /**
     * Records a property the endpoint does not accept.
     *
     * @param name  property name as sent
     * @param value its value, not kept
     */
    @JsonAnySetter
    public void unknownProperty(String name, Object value) {
        unknownProperties.add(name);
    }

    /**
     * Properties of the body that cannot be changed here, in alphabetical order.
     *
     * @return the property names
     */
    public SortedSet<String> unknownProperties() {
        return Collections.unmodifiableSortedSet(unknownProperties);
    }
}
