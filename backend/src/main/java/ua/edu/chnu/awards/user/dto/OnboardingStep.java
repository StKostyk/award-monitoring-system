package ua.edu.chnu.awards.user.dto;

/**
 * One getting-started step of the caller.
 *
 * @param key     the step
 * @param done    true when the data proves it or the caller marked it done
 * @param derived true when the data decides the step, so it is never stored
 */
public record OnboardingStep(OnboardingStepKey key, boolean done, boolean derived) {
}
