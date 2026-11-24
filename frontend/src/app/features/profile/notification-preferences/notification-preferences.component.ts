import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatSlideToggle, MatSlideToggleChange } from '@angular/material/slide-toggle';
import { TranslocoPipe } from '@jsverse/transloco';

import { problemType } from '../../../core/api/problem';
import {
  NotificationApi,
  NotificationPreference,
  NotificationType,
} from '../../../core/notifications/notification.api';

export type Channel = 'email' | 'inApp';

/** A heading and the notification types listed under it. */
export interface PreferenceGroup {
  key: string;
  types: readonly NotificationType[];
}

export const PREFERENCE_GROUPS: readonly PreferenceGroup[] = [
  {
    key: 'myAwards',
    types: [
      'AWARD_APPROVED',
      'AWARD_REJECTED',
      'AWARD_RETURNED',
      'AWARD_ESCALATED',
      'AWARD_CORRECTED',
      'AWARDS_REMINDER',
    ],
  },
  { key: 'reviewing', types: ['REVIEW_OVERDUE', 'REVIEW_TAKEN_OVER'] },
  {
    key: 'roles',
    types: ['ROLE_ASSIGNED', 'ROLE_REVOKED', 'DELEGATION_CREATED', 'DELEGATION_REVOKED'],
  },
];

/** The number of events a person chooses channels for. */
export const PREFERENCE_COUNT = PREFERENCE_GROUPS.reduce(
  (sum, group) => sum + group.types.length,
  0,
);

const CHANNELS: readonly Channel[] = ['email', 'inApp'];

/**
 * The profile section where a person switches e-mail and in-app delivery per notification type; each switch is
 * saved at once, and the last channel of a type cannot be switched off.
 */
@Component({
  selector: 'app-notification-preferences',
  imports: [MatSlideToggle, MatButton, TranslocoPipe],
  templateUrl: './notification-preferences.component.html',
  styleUrl: './notification-preferences.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationPreferencesComponent {
  private readonly api = inject(NotificationApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly groups = PREFERENCE_GROUPS;
  readonly channels = CHANNELS;
  readonly current = signal<NotificationPreference[]>([]);
  readonly loadFailed = signal(false);
  readonly saving = signal<NotificationType | null>(null);
  readonly message = signal<string | null>(null);
  readonly problem = signal<string | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    this.loadFailed.set(false);
    this.api
      .preferences()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (preferences) => this.current.set(preferences),
        error: () => this.loadFailed.set(true),
      });
  }

  preference(type: NotificationType): NotificationPreference | undefined {
    return this.current().find((preference) => preference.type === type);
  }

  checked(type: NotificationType, channel: Channel): boolean {
    return this.preference(type)?.[channel] ?? false;
  }

  /**
   * Saves one switch at once; switching off the last channel of a type is refused and the switch goes back on.
   *
   * @param type the notification type
   * @param channel the switched channel
   * @param event the switch change
   */
  toggle(type: NotificationType, channel: Channel, event: MatSlideToggleChange): void {
    const stored = this.preference(type);
    this.message.set(null);
    this.problem.set(null);
    if (!stored || this.saving() !== null) {
      event.source.checked = this.checked(type, channel);
      return;
    }
    const other: Channel = channel === 'email' ? 'inApp' : 'email';
    if (!event.checked && !stored[other]) {
      event.source.checked = true;
      this.problem.set('notifications.preferences.lastChannel');
      return;
    }
    const next: NotificationPreference = { ...stored, [channel]: event.checked };
    this.replace(next);
    this.saving.set(type);
    this.api
      .savePreferences([next])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (preferences) => {
          this.current.set(preferences);
          this.message.set('notifications.preferences.saved');
          this.saving.set(null);
        },
        error: (err: unknown) => {
          this.replace(stored);
          event.source.checked = stored[channel];
          this.problem.set(
            problemType(err) === 'network'
              ? 'notifications.preferences.errors.network'
              : 'notifications.preferences.errors.failed',
          );
          this.saving.set(null);
        },
      });
  }

  private replace(next: NotificationPreference): void {
    this.current.update((list) =>
      list.map((preference) => (preference.type === next.type ? next : preference)),
    );
  }
}
