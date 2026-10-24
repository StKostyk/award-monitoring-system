package ua.edu.chnu.awards.common.erasure;

/**
 * The transaction-scoped advisory lock that erasures and the revocation of a role the university cannot be
 * without take, so neither can remove the last holder while the other is running.
 */
public final class ErasureLockConstants {

    /** Takes the lock until the transaction ends. */
    public static final String LOCK = "select pg_advisory_xact_lock(hashtext('erasure-run'))";

    private ErasureLockConstants() {
    }
}
