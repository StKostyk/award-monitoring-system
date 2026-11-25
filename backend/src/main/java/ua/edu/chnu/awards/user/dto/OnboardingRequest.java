package ua.edu.chnu.awards.user.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The getting-started progress the caller sends, stored as it is after validation (schema OnboardingUpdate).
 *
 * @param done      names of the steps marked done; a missing list is empty
 * @param dismissed true when the checklist was hidden for good
 * @param tourSeen  true when the screen tour was finished or skipped
 */
public record OnboardingRequest(List<String> done, boolean dismissed, boolean tourSeen) {

    /**
     * Keeps an unmodifiable copy, null names included for the service to reject; a missing list is empty.
     *
     * @param done      names of the steps marked done
     * @param dismissed true when the checklist was hidden for good
     * @param tourSeen  true when the screen tour was finished or skipped
     */
    public OnboardingRequest {
        done = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNullElse(done, List.of())));
    }
}
