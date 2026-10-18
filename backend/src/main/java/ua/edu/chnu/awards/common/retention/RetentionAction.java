package ua.edu.chnu.awards.common.retention;

/**
 * What a retention policy does with the records it selects.
 */
public enum RetentionAction {
    DELETE("delete"),
    ERASE_ACCOUNT("erase-account"),
    CREATE_PARTITIONS("create-partitions"),
    DROP_PARTITIONS("drop-partitions");

    private final String code;

    RetentionAction(String code) {
        this.code = code;
    }

    /**
     * The name shown on the compliance page.
     *
     * @return the code, such as {@code delete}
     */
    public String code() {
        return code;
    }
}
