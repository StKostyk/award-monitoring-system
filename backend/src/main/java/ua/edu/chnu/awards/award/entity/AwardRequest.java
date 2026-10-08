package ua.edu.chnu.awards.award.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import ua.edu.chnu.awards.user.entity.User;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * The approval request of a submitted award; one per award.
 */
@Entity
@Table(name = "award_requests")
@DynamicUpdate
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(exclude = {"award", "submitter", "currentReviewer"})
public class AwardRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "award_id", nullable = false, updatable = false)
    private Award award;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitter_id", nullable = false, updatable = false)
    private User submitter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_reviewer_id")
    private User currentReviewer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RequestStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_level", nullable = false, length = 30)
    private ApprovalLevel currentLevel;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "deadline")
    private Instant deadline;

    @Setter(AccessLevel.NONE)
    @Column(name = "overdue_noticed_at")
    private Instant overdueNoticedAt;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "overdue_noticed_level", length = 30)
    private ApprovalLevel overdueNoticedLevel;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder.Default
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    /**
     * Starts a new review period: sets the deadline and clears the overdue notice of the previous one.
     *
     * @param newDeadline the deadline of the new period, null when no review is pending
     */
    public void restartPeriod(Instant newDeadline) {
        deadline = newDeadline;
        overdueNoticedAt = null;
        overdueNoticedLevel = null;
    }

    /**
     * Whether the request still waits for a decision.
     *
     * @return true while submitted, in review or escalated
     */
    public boolean isOpen() {
        return status == RequestStatus.SUBMITTED || status == RequestStatus.IN_REVIEW
            || status == RequestStatus.ESCALATED;
    }

    /**
     * Whether the request reached an outcome that no reviewer changes any more.
     *
     * @return true when approved, rejected or expired
     */
    public boolean isFinal() {
        return status == RequestStatus.APPROVED || status == RequestStatus.REJECTED
            || status == RequestStatus.EXPIRED;
    }
}
