package ua.edu.chnu.awards.common.retention;

/**
 * The counts of one policy in one run.
 *
 * @param policy   the policy key
 * @param affected records deleted, erased or partitions created or dropped, in committed batches only
 * @param held     records kept because their person is under a legal hold
 * @param failed   whether the policy stopped on an error
 * @param error    the class of that error, never its message; null without an error
 */
public record RetentionResult(String policy, int affected, int held, boolean failed, String error) {

    /**
     * A policy that completed.
     *
     * @param policy   the policy key
     * @param affected records affected
     * @param held     records kept for a hold
     * @return the result
     */
    public static RetentionResult completed(String policy, int affected, int held) {
        return new RetentionResult(policy, affected, held, false, null);
    }

    /**
     * A policy that stopped on an error after some batches committed.
     *
     * @param policy   the policy key
     * @param affected records affected by the committed batches
     * @param error    the error
     * @return the result, naming the error class only
     */
    public static RetentionResult failed(String policy, int affected, Throwable error) {
        return new RetentionResult(policy, affected, 0, true, error.getClass().getName());
    }
}
