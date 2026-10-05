package ua.edu.chnu.awards.document.service;

import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.common.web.ApiProblemException;

/**
 * The malware scanner did not answer in time or could not scan the file: 503 {@code scanner-unavailable}. The
 * upload is refused, since a file that was not scanned is not stored.
 */
public class ScannerUnavailableException extends ApiProblemException {

    private static final long serialVersionUID = 1L;

    /**
     * A failure the scanner reported in its reply.
     *
     * @param reply what {@code clamd} answered
     */
    public ScannerUnavailableException(String reply) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "scanner-unavailable", "The malware scanner is not available",
            new IllegalStateException(reply));
    }

    /**
     * Wraps the connection failure.
     *
     * @param cause what the socket reported
     */
    public ScannerUnavailableException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "scanner-unavailable", "The malware scanner is not available",
            cause);
    }
}
