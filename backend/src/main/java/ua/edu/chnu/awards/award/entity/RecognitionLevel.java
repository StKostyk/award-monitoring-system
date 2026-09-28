package ua.edu.chnu.awards.award.entity;

/**
 * Recognition level of an award category with the lowest approval level that may give the final approval
 * (every level above it may approve too) and the base of the award's impact score.
 */
public enum RecognitionLevel {
    SPECIALITY(ApprovalLevel.FACULTY_SECRETARY, 10),
    DEPARTMENT(ApprovalLevel.FACULTY_SECRETARY, 20),
    COLLEGE(ApprovalLevel.DEAN, 30),
    FACULTY(ApprovalLevel.DEAN, 40),
    LOCAL(ApprovalLevel.FACULTY_SECRETARY, 45),
    UNIVERSITY(ApprovalLevel.FACULTY_SECRETARY, 60),
    REGIONAL(ApprovalLevel.FACULTY_SECRETARY, 70),
    NATIONAL(ApprovalLevel.RECTOR_SECRETARY, 80),
    INTERNATIONAL(ApprovalLevel.RECTOR_SECRETARY, 100);

    private final ApprovalLevel minimumApproval;
    private final int baseScore;

    RecognitionLevel(ApprovalLevel minimumApproval, int baseScore) {
        this.minimumApproval = minimumApproval;
        this.baseScore = baseScore;
    }

    /**
     * Lowest approval level that may approve an award of this level.
     *
     * @return the approval level
     */
    public ApprovalLevel minimumApproval() {
        return minimumApproval;
    }

    /**
     * Impact score of an award of this level before organisation modifiers.
     *
     * @return score between 0 and 100
     */
    public int baseScore() {
        return baseScore;
    }
}
