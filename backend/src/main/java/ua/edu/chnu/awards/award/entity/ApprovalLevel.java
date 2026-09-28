package ua.edu.chnu.awards.award.entity;

/**
 * Approval levels of the award workflow in the order a request climbs them (`award_requests.current_level`).
 */
public enum ApprovalLevel {
    FACULTY_SECRETARY,
    DEAN,
    RECTOR_SECRETARY,
    RECTOR
}
