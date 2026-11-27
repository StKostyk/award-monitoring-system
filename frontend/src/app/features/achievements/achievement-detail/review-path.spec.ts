import { AwardStatusView, AwardVersion, ReviewDecision } from '../../awards/awards.service';
import { reviewPath } from './review-path';

function view(overrides: Partial<AwardStatusView> = {}): AwardStatusView {
  return {
    awardId: 41,
    status: 'APPROVED',
    requestStatus: 'APPROVED',
    currentLevel: 'DEAN',
    submittedAt: '2026-11-03T09:00:00Z',
    deadline: null,
    estimatedCompletion: null,
    overdue: false,
    completedAt: '2026-11-04T12:00:00Z',
    rejectionReason: null,
    delay: null,
    path: [],
    decisions: [],
    ...overrides,
  };
}

function decision(overrides: Partial<ReviewDecision>): ReviewDecision {
  return {
    id: 1,
    decision: 'APPROVED',
    level: 'DEAN',
    reviewerId: 9,
    reviewerName: 'Олена Декан',
    comments: null,
    decidedAt: '2026-11-04T12:00:00Z',
    delegatorId: null,
    delegatorName: null,
    ...overrides,
  };
}

function submitted(createdAt: string): AwardVersion {
  return {
    number: 2,
    action: 'SUBMITTED',
    actor: null,
    createdAt,
    snapshot: {} as AwardVersion['snapshot'],
    changes: [],
    comment: null,
  };
}

describe('reviewPath', () => {
  it('ac2_orders_submissions_returns_and_the_final_decision_by_time', () => {
    const returned = decision({
      id: 1,
      decision: 'RETURNED',
      level: 'FACULTY_SECRETARY',
      comments: 'Додайте диплом',
      decidedAt: '2026-11-02T10:00:00Z',
    });
    const approved = decision({ id: 2, decidedAt: '2026-11-04T12:00:00Z' });
    const versions = [submitted('2026-11-03T09:00:00Z'), submitted('2026-11-01T08:00:00Z')];

    const steps = reviewPath(view({ decisions: [approved, returned] }), versions);

    expect(steps).toEqual([
      { kind: 'submitted', at: '2026-11-01T08:00:00Z' },
      {
        kind: 'decision',
        at: '2026-11-02T10:00:00Z',
        decision: 'RETURNED',
        level: 'FACULTY_SECRETARY',
        comment: 'Додайте диплом',
      },
      { kind: 'resubmitted', at: '2026-11-03T09:00:00Z' },
      {
        kind: 'decision',
        at: '2026-11-04T12:00:00Z',
        decision: 'APPROVED',
        level: 'DEAN',
        comment: null,
      },
    ]);
  });

  it('ac3_steps_never_carry_the_reviewer_name', () => {
    const steps = reviewPath(view({ decisions: [decision({})] }), []);

    expect(JSON.stringify(steps)).not.toContain('Олена Декан');
  });

  it('ac2_without_submitted_versions_uses_the_request_submission', () => {
    const steps = reviewPath(view(), [{ ...submitted('2026-11-01T08:00:00Z'), action: 'UPDATED' }]);

    expect(steps).toEqual([{ kind: 'submitted', at: '2026-11-03T09:00:00Z' }]);
  });

  it('ac2_never_submitted_award_has_no_steps', () => {
    expect(reviewPath(view({ submittedAt: null, requestStatus: null }), [])).toEqual([]);
  });

  it('ac2_a_decision_at_the_submission_time_follows_it', () => {
    const at = '2026-11-03T09:00:00Z';
    const steps = reviewPath(view({ decisions: [decision({ decidedAt: at })] }), [submitted(at)]);

    expect(steps.map((step) => step.kind)).toEqual(['submitted', 'decision']);
  });

  it('ac2_fallback_after_earlier_decisions_is_a_resubmission', () => {
    const returned = decision({ decision: 'RETURNED', decidedAt: '2026-11-02T10:00:00Z' });

    const steps = reviewPath(view({ decisions: [returned] }), []);

    expect(steps.map((step) => step.kind)).toEqual(['decision', 'resubmitted']);
  });
});
