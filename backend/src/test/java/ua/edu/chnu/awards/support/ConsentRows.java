package ua.edu.chnu.awards.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test cleanup of the append-only consent history: the guard trigger is bypassed for one statement by running it
 * as a replication session, which is open to the superuser of the test container only.
 */
public final class ConsentRows {

    private ConsentRows() {
    }

    /**
     * Deletes every consent record of a person.
     *
     * @param jdbc   the template
     * @param userId the person
     */
    public static void purge(JdbcTemplate jdbc, long userId) {
        jdbc.execute("DO $$ BEGIN SET LOCAL session_replication_role = replica; "
            + "DELETE FROM consent_records WHERE user_id = " + userId + "; END $$");
    }
}
