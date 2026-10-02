package ua.edu.chnu.awards.document.service;

import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.common.web.ApiProblemException;

/**
 * The object storage did not answer or refused a call: 503 {@code storage-unavailable}.
 */
public class StorageUnavailableException extends ApiProblemException {

    private static final long serialVersionUID = 1L;

    /**
     * Wraps the client's failure.
     *
     * @param cause what the storage client reported
     */
    public StorageUnavailableException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "storage-unavailable", "The document storage is not available",
            cause);
    }
}
