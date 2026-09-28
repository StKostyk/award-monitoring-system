package ua.edu.chnu.awards.award.entity;

/**
 * Status of an approval request ({@code award_requests.status}).
 */
public enum RequestStatus {
    SUBMITTED,
    IN_REVIEW,
    ESCALATED,
    APPROVED,
    REJECTED,
    RETURNED,
    EXPIRED
}
