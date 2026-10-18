package ua.edu.chnu.awards.gdpr.service;

import java.util.List;
import java.util.function.Function;

import ua.edu.chnu.awards.common.retention.RetentionAction;
import ua.edu.chnu.awards.common.retention.RetentionContext;
import ua.edu.chnu.awards.common.retention.RetentionPolicy;
import ua.edu.chnu.awards.common.retention.RetentionResult;

/**
 * A retention policy whose work is given by the test; it records its key when applied.
 *
 * @param key   the policy key
 * @param order the position
 * @param body  what applying does
 * @param calls where the key is recorded
 */
record FakeRetentionPolicy(String key, int order, Function<RetentionContext, RetentionResult> body,
                           List<String> calls) implements RetentionPolicy {

    @Override
    public RetentionAction action() {
        return RetentionAction.DELETE;
    }

    @Override
    public String period() {
        return "P1D";
    }

    @Override
    public RetentionResult apply(RetentionContext context) {
        calls.add(key);
        return body.apply(context);
    }
}
