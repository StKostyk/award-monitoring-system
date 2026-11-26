package ua.edu.chnu.awards.common.erasure;

/**
 * One module's part of an account erasure. The erasure runner calls every contributor in ascending
 * {@link #order()} inside one transaction, so either every module erases the person or none does.
 */
public interface ErasureContributor {

    /**
     * Where this contributor runs; see {@link ErasureOrderConstants}.
     *
     * @return the position, lower first
     */
    int order();

    /**
     * Erases or anonymises what this module holds about the person, joining the caller's transaction, and reports
     * counts, names and the identifiers of deleted records into the context.
     *
     * @param context the person and what the earlier contributors reported
     */
    void erase(ErasureContext context);
}
