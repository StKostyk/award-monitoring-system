package ua.edu.chnu.awards.support;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Polls a value until it reaches an expected state or a deadline passes, for tests of asynchronous listeners.
 */
public final class Eventually {

    /**
     * The usual wait for an event listener or a mail to finish.
     */
    public static final Duration LISTENER_WAIT = Duration.ofSeconds(30);

    private static final Duration POLL = Duration.ofMillis(100);

    private Eventually() {
    }

    /**
     * Polls the value until it equals the expected one.
     *
     * @param value    reads the current value
     * @param expected the value to wait for
     * @param timeout  how long to wait
     * @param <T>      value type
     * @return the last value read, the expected one unless the deadline passed
     */
    public static <T> T equalTo(Supplier<T> value, T expected, Duration timeout) {
        return attempt(() -> Optional.ofNullable(value.get()).filter(expected::equals), timeout, POLL)
            .orElseGet(value);
    }

    /**
     * Polls the value with the usual listener wait until it equals the expected one.
     *
     * @param value    reads the current value
     * @param expected the value to wait for
     * @param <T>      value type
     * @return the last value read, the expected one unless the deadline passed
     */
    public static <T> T equalTo(Supplier<T> value, T expected) {
        return equalTo(value, expected, LISTENER_WAIT);
    }

    /**
     * Repeats an attempt until it yields a result.
     *
     * @param attempt one try, empty when the result is not there yet
     * @param timeout how long to keep trying
     * @param pause   pause between tries
     * @param <T>     result type
     * @return the first result, empty when the deadline passed or the thread was interrupted
     */
    public static <T> Optional<T> attempt(Supplier<Optional<T>> attempt, Duration timeout, Duration pause) {
        long deadline = System.nanoTime() + timeout.toNanos();
        Optional<T> result = attempt.get();
        while (result.isEmpty() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(pause.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
            result = attempt.get();
        }
        return result;
    }
}
