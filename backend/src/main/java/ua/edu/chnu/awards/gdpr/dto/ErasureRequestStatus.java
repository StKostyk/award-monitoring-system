package ua.edu.chnu.awards.gdpr.dto;

/**
 * The state of an erasure request; a postponed request stays pending.
 */
public enum ErasureRequestStatus {
    PENDING,
    CANCELLED,
    COMPLETED
}
