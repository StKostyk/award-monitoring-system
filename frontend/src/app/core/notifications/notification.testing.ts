import { AppNotification } from './notification.api';

/** A dean's approval of award `id + 100`, sent just now; `changes` replace any field. */
export function notificationItem(
  id: number,
  read: boolean,
  changes: Partial<AppNotification> = {},
): AppNotification {
  return {
    id,
    type: 'AWARD_APPROVED',
    payload: { title: `Award ${id}`, reviewer: 'Аліна', level: 'DEAN' },
    referenceType: 'AWARD',
    referenceId: id + 100,
    read,
    sentAt: new Date().toISOString(),
    ...changes,
  };
}
