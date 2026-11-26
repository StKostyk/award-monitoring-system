package ua.edu.chnu.awards.auth.event;

import java.time.Instant;

/**
 * A user signed in from a browser not seen before; the user must be told once the transaction commits.
 *
 * @param email           recipient
 * @param firstName       used in the greeting
 * @param browser         browser family
 * @param operatingSystem operating-system family
 * @param ip              client address of the sign-in
 * @param at              moment of the sign-in
 * @param link            the "this was not me" link containing the raw token
 */
public record NewDeviceSignedIn(String email, String firstName, String browser, String operatingSystem, String ip,
                                Instant at, String link) {
}
