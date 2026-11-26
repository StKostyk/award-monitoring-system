package ua.edu.chnu.awards.document.service;

import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.common.web.ApiProblemException;

/**
 * A document that does not exist or that the caller may not see: 404 {@code document-not-found}.
 */
public class DocumentNotFoundException extends ApiProblemException {

    private static final long serialVersionUID = 1L;

    /**
     * The problem for one document.
     *
     * @param id the document
     */
    public DocumentNotFoundException(long id) {
        this(id, null);
    }

    /**
     * The problem for one document, found while checking its award.
     *
     * @param id    the document
     * @param cause why the award was refused
     */
    public DocumentNotFoundException(long id, Throwable cause) {
        super(HttpStatus.NOT_FOUND, "document-not-found", "Document " + id + " not found", cause);
    }
}
