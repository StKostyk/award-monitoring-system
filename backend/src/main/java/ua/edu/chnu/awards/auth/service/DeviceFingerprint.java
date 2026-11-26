package ua.edu.chnu.awards.auth.service;

import java.util.Locale;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.HashUtils;

import ua_parser.Client;
import ua_parser.Parser;

/**
 * Turns request headers into a device description: browser and operating-system families (versions ignored)
 * and a SHA-256 fingerprint of both together with the accepted languages.
 */
@Component
public class DeviceFingerprint {

    private static final int NAME_LENGTH = 100;

    private final Parser parser = new Parser();

    /**
     * Describes the browser behind a request.
     *
     * @param userAgent      the {@code User-Agent} header, may be null
     * @param acceptLanguage the {@code Accept-Language} header, may be null
     * @return the device with its fingerprint
     */
    public Device of(String userAgent, String acceptLanguage) {
        Client client = parser.parse(userAgent == null ? "" : userAgent);
        String browser = trim(client.userAgent.family);
        String os = trim(client.os.family);
        String language = acceptLanguage == null ? "" : acceptLanguage.trim().toLowerCase(Locale.ROOT);
        return new Device(HashUtils.sha256Hex(browser + '|' + os + '|' + language), browser, os);
    }

    private static String trim(String name) {
        String value = name == null ? "Other" : name;
        return value.length() > NAME_LENGTH ? value.substring(0, NAME_LENGTH) : value;
    }

    /**
     * A described browser.
     *
     * @param fingerprint     SHA-256 hex digest identifying the browser for one user
     * @param browser         browser family
     * @param operatingSystem operating-system family
     */
    public record Device(String fingerprint, String browser, String operatingSystem) {
    }
}
