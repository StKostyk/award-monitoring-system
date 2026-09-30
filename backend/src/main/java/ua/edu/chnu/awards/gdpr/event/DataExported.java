package ua.edu.chnu.awards.gdpr.event;

import java.time.Instant;

/**
 * A person downloaded their data; they must be told once the transaction commits.
 *
 * @param email     recipient
 * @param firstName used in the greeting
 * @param at        moment of the export
 * @param ip        client address of the download
 * @param browser   browser family of the download
 */
public record DataExported(String email, String firstName, Instant at, String ip, String browser) {
}
