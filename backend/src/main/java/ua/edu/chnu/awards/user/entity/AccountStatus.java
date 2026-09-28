package ua.edu.chnu.awards.user.entity;

/**
 * Lifecycle state of a user account, see the user account state machine.
 */
public enum AccountStatus {
    PENDING(false),
    ACTIVE(true),
    INACTIVE(false),
    SUSPENDED(false),
    RETIRED(true),
    MEMORIAL(false),
    DELETED(false);

    private final boolean loginAllowed;

    AccountStatus(boolean loginAllowed) {
        this.loginAllowed = loginAllowed;
    }

    /**
     * Whether a user in this state may authenticate.
     *
     * @return true for states that allow signing in
     */
    public boolean canLogIn() {
        return loginAllowed;
    }

    /**
     * Whether accounts in this state appear in the user directory: unverified and deleted ones never do.
     *
     * @return false for {@code PENDING} and {@code DELETED}
     */
    public boolean isListed() {
        return this != PENDING && this != DELETED;
    }
}
