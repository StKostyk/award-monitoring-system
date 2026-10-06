package ua.edu.chnu.awards.award.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

import ua.edu.chnu.awards.user.entity.User;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * One reviewer decision on an approval request. The application reads these rows; the review workflow writes
 * them, and they never change afterwards.
 */
@Entity
@Table(name = "review_decisions")
@Immutable
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(exclude = "reviewer")
public class ReviewDecision {

    @Id
    @Column(name = "decision_id")
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20)
    private ReviewDecisionType decision;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 30)
    private ApprovalLevel level;

    @Column(name = "comments")
    private String comments;

    @Column(name = "delegator_id")
    private Long delegatorId;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;
}
