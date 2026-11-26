package ua.edu.chnu.awards.user.dto;

import java.util.List;

/**
 * The caller's getting-started steps.
 *
 * @param steps     the steps of the caller's roles in order
 * @param dismissed true when the checklist was hidden for good
 * @param tourSeen  true when the screen tour was finished or skipped
 */
public record OnboardingState(List<OnboardingStep> steps, boolean dismissed, boolean tourSeen) {

    /**
     * Copies the steps.
     *
     * @param steps     the steps of the caller's roles in order
     * @param dismissed true when the checklist was hidden for good
     * @param tourSeen  true when the screen tour was finished or skipped
     */
    public OnboardingState {
        steps = List.copyOf(steps);
    }
}
