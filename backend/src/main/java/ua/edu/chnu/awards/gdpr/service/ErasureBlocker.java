package ua.edu.chnu.awards.gdpr.service;

import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.common.web.ApiProblemException;

/**
 * Why an account cannot be erased now: the request is refused while it is new and postponed once it is due.
 */
enum ErasureBlocker {

    LEGAL_HOLD("legal-hold", true, "The account cannot be erased now"),
    LAST_ADMINISTRATOR("last-administrator", false,
        "The only system administrator cannot be erased; appoint another one first");

    private final String code;
    private final boolean notifies;
    private final String message;

    ErasureBlocker(String code, boolean notifies, String message) {
        this.code = code;
        this.notifies = notifies;
        this.message = message;
    }

    /**
     * The problem type of a refused request and the reason of a postponement in the audit trail.
     *
     * @return the code
     */
    String code() {
        return code;
    }

    /**
     * Whether the person is told by mail that the due erasure is postponed.
     *
     * @return true when a mail goes out
     */
    boolean notifies() {
        return notifies;
    }

    /**
     * The refusal of a new erasure request.
     *
     * @return a 409 problem with the code
     */
    ApiProblemException problem() {
        return new ApiProblemException(HttpStatus.CONFLICT, code, message);
    }
}
