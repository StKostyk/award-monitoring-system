package ua.edu.chnu.awards.common.retention;

/**
 * A nightly job that runs once more shortly after a start, so that a server that was off at night, or a database
 * restored from a backup, catches up before the next night.
 */
public interface CatchUpJob {

    /**
     * The name in the catch-up log line.
     *
     * @return a short name of the job
     */
    String catchUpName();

    /**
     * The position of the job in the catch-up; lower runs first.
     *
     * @return one of {@link CatchUpOrderConstants}
     */
    int catchUpOrder();

    /**
     * One run of the job.
     *
     * @return a short summary of what the run did, for the log
     */
    String catchUp();
}
