import {
  ApprovalLevel,
  AwardStatusView,
  AwardVersion,
  ReviewDecisionType,
} from '../../awards/awards.service';

/** One step of the review path: a submission or a reviewer decision, without the reviewer's name. */
export type ReviewPathStep =
  | { kind: 'submitted' | 'resubmitted'; at: string }
  | {
      kind: 'decision';
      at: string;
      decision: ReviewDecisionType;
      level: ApprovalLevel;
      comment: string | null;
    };

/**
 * The recorded review history of an award, oldest first: every submission from the saved versions and every
 * reviewer decision with its role and comment. Without a recorded submission the request's latest one stands in,
 * as a resubmission when decisions precede it.
 *
 * @param view the review status of the award
 * @param versions its saved versions, in any order
 * @return the steps; empty for an award that was never submitted
 */
export function reviewPath(view: AwardStatusView, versions: AwardVersion[]): ReviewPathStep[] {
  const submissions = versions
    .filter((version) => version.action === 'SUBMITTED')
    .map((version) => version.createdAt);
  let earlier = false;
  if (submissions.length === 0 && view.submittedAt) {
    const submittedAt = Date.parse(view.submittedAt);
    submissions.push(view.submittedAt);
    earlier = view.decisions.some((decision) => Date.parse(decision.decidedAt) < submittedAt);
  }
  const steps: ReviewPathStep[] = submissions
    .sort((a, b) => Date.parse(a) - Date.parse(b))
    .map((at, index) => ({ kind: index === 0 && !earlier ? 'submitted' : 'resubmitted', at }));
  for (const decision of view.decisions) {
    steps.push({
      kind: 'decision',
      at: decision.decidedAt,
      decision: decision.decision,
      level: decision.level,
      comment: decision.comments,
    });
  }
  return steps
    .map((step, index) => ({ step, index }))
    .sort((a, b) => Date.parse(a.step.at) - Date.parse(b.step.at) || a.index - b.index)
    .map(({ step }) => step);
}
