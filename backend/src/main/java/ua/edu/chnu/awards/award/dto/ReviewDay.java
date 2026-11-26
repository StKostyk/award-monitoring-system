package ua.edu.chnu.awards.award.dto;

import java.time.Instant;

/**
 * The caller's review work of the current Kyiv day.
 *
 * @param decided the decisions made since the start of the day
 * @param since   the start of the day
 */
public record ReviewDay(long decided, Instant since) {
}
