const PREFIX = 'awards.decision-draft';

/**
 * Reads the comment kept in the session storage of the tab for one decision.
 *
 * @param scope the award id, or `batch` for the batch dialog
 * @param decision the decision the comment belongs to
 * @returns the kept comment, or an empty string
 */
export function readDraft(scope: string, decision: string): string {
  try {
    return sessionStorage.getItem(draftKey(scope, decision)) ?? '';
  } catch {
    return '';
  }
}

/**
 * Keeps the comment of one decision in the session storage of the tab; a blank comment removes it.
 *
 * @param scope the award id, or `batch` for the batch dialog
 * @param decision the decision the comment belongs to
 * @param comment the comment typed so far
 */
export function writeDraft(scope: string, decision: string, comment: string): void {
  try {
    if (comment.trim()) {
      sessionStorage.setItem(draftKey(scope, decision), comment);
    } else {
      sessionStorage.removeItem(draftKey(scope, decision));
    }
  } catch {
    return;
  }
}

/**
 * Removes the kept comment of one decision.
 *
 * @param scope the award id, or `batch` for the batch dialog
 * @param decision the decision the comment belongs to
 */
export function clearDraft(scope: string, decision: string): void {
  writeDraft(scope, decision, '');
}

function draftKey(scope: string, decision: string): string {
  return `${PREFIX}:${scope}:${decision}`;
}
