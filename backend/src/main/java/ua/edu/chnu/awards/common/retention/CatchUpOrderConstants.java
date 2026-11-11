package ua.edu.chnu.awards.common.retention;

/**
 * The positions of the nightly jobs in the catch-up at start: erasure first, so that a restored account whose
 * erasure fell due is gone before the retention run counts it, then retention, then the document sweep, which removes
 * the objects the first two left without a row.
 */
@SuppressWarnings("PMD.DataClass")
public final class CatchUpOrderConstants {

    /** The erasure job. */
    public static final int ERASURE = 10;

    /** The retention job. */
    public static final int RETENTION = 20;

    /** The document sweep. */
    public static final int DOCUMENT_SWEEP = 30;

    private CatchUpOrderConstants() {
    }
}
