package ua.edu.chnu.awards.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeviceFingerprintTest {

    private static final String CHROME_WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final String CHROME_WINDOWS_NEWER = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36";
    private static final String FIREFOX_MAC = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14.6; rv:130.0) "
        + "Gecko/20100101 Firefox/130.0";
    private static final String LANGUAGE = "uk-UA,uk;q=0.9,en;q=0.8";

    private final DeviceFingerprint fingerprints = new DeviceFingerprint();

    @Test
    void ac51_namesBrowserAndOperatingSystemFamilies() {
        DeviceFingerprint.Device chrome = fingerprints.of(CHROME_WINDOWS, LANGUAGE);
        DeviceFingerprint.Device firefox = fingerprints.of(FIREFOX_MAC, LANGUAGE);

        assertThat(chrome.browser()).isEqualTo("Chrome");
        assertThat(chrome.operatingSystem()).isEqualTo("Windows");
        assertThat(firefox.browser()).isEqualTo("Firefox");
        assertThat(firefox.operatingSystem()).isEqualTo("Mac OS X");
        assertThat(chrome.fingerprint()).hasSize(64).matches("[0-9a-f]+").isNotEqualTo(firefox.fingerprint());
    }

    @Test
    void ac51_ac52_fingerprintIgnoresVersionsButNotLanguage() {
        String reference = fingerprints.of(CHROME_WINDOWS, LANGUAGE).fingerprint();

        assertThat(fingerprints.of(CHROME_WINDOWS_NEWER, LANGUAGE).fingerprint()).isEqualTo(reference);
        assertThat(fingerprints.of(CHROME_WINDOWS, " UK-UA,uk;q=0.9,en;q=0.8 ").fingerprint()).isEqualTo(reference);
        assertThat(fingerprints.of(CHROME_WINDOWS, "en-US,en;q=0.9").fingerprint()).isNotEqualTo(reference);
    }

    @Test
    void ac51_missingHeadersStillProduceAStableFingerprint() {
        DeviceFingerprint.Device unknown = fingerprints.of(null, null);

        assertThat(unknown.browser()).isEqualTo("Other");
        assertThat(unknown.operatingSystem()).isEqualTo("Other");
        assertThat(unknown.fingerprint()).isEqualTo(fingerprints.of("", "").fingerprint());
    }
}
