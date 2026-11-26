package ua.edu.chnu.awards.award.controller;

/**
 * {@code @PreAuthorize} expressions of the award endpoints, shared by the award, history and document
 * controllers.
 */
@SuppressWarnings("PMD.DataClass")
public final class AwardPermissionConstants {

    /** Creating an award of one's own. */
    public static final String CAN_CREATE = "@access.require('award:create')";
    /** Changing one's own draft and its documents. */
    public static final String CAN_UPDATE = "@access.require('award:update:own')";
    /** Reading an award; the services narrow it to the caller's own awards and scope. */
    public static final String CAN_READ_OWN = "@access.require('award:read:own')";
    /** Reviewing requests at some level, by an own or a borrowed role; the services apply the reviewer rule. */
    public static final String CAN_REVIEW = "@access.require('award:approve:level1')";

    private AwardPermissionConstants() {
    }
}
