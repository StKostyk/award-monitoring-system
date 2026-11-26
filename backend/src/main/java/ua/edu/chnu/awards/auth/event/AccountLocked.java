package ua.edu.chnu.awards.auth.event;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * An account was locked after repeated failed logins; the administrators must be told.
 *
 * @param email      the locked account
 * @param ip         address of the last failed attempt
 * @param at         moment of the lock
 * @param lockedFor  how long the lock lasts
 * @param recipients addresses of every current system administrator
 */
public record AccountLocked(String email, String ip, Instant at, Duration lockedFor, List<String> recipients) {

    public AccountLocked {
        recipients = List.copyOf(recipients);
    }
}
