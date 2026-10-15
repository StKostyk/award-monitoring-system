import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatCheckbox } from '@angular/material/checkbox';
import { ActivatedRoute } from '@angular/router';
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
    ],
  },
  { key: 'reviewing', types: ['REVIEW_OVERDUE', 'REVIEW_TAKEN_OVER'] },
  {
    key: 'roles',
    types: ['ROLE_ASSIGNED', 'ROLE_REVOKED', 'DELEGATION_CREATED', 'DELEGATION_REVOKED'],
  },
];

const ANCHOR = 'notifications';
const CHANNELS: readonly Channel[] = ['email', 'inApp'];

/** The profile card where a person chooses e-mail and in-app delivery per notification type. */
@Component({
  selector: 'app-notification-preferences',
  imports: [
    MatCard,
    MatCardHeader,
    MatCardTitle,
    MatCardContent,
    MatCheckbox,
    MatButton,
    TranslocoPipe,
  ],
  templateUrl: './notification-preferences.component.html',
  styleUrl: './notification-preferences.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationPreferencesComponent {
  private readonly api = inject(NotificationApi);
  private readonly route = inject(ActivatedRoute);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destroyRef = inject(DestroyRef);
  private scrollTimer: ReturnType<typeof setTimeout> | undefined;

  readonly groups = PREFERENCE_GROUPS;
  readonly channels = CHANNELS;
  readonly current = signal<NotificationPreference[]>([]);
  readonly loaded = signal<NotificationPreference[]>([]);
  readonly loadFailed = signal(false);
  readonly saving = signal(false);
  readonly message = signal<string | null>(null);
  readonly problem = signal<string | null>(null);
  readonly changed = computed(() =>
    this.current().filter((preference) => {
      const stored = this.loaded().find((other) => other.type === preference.type);
      return stored?.email !== preference.email || stored.inApp !== preference.inApp;
    }),
  );
  readonly dirty = computed(() => this.changed().length > 0);

  constructor() {
    this.destroyRef.onDestroy(() => clearTimeout(this.scrollTimer));
    this.load();
  }

  load(): void {
    this.loadFailed.set(false);
    this.api
      .preferences()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (preferences) => {
          this.show(preferences);
          if (this.route.snapshot.fragment === ANCHOR) {
            this.scrollTimer = setTimeout(() => this.host.nativeElement.scrollIntoView());
          }
        },
        error: () => this.loadFailed.set(true),
      });
  }

  preference(type: NotificationType): NotificationPreference | undefined {
    return this.current().find((preference) => preference.type === type);
  }

  checked(type: NotificationType, channel: Channel): boolean {
    return this.preference(type)?.[channel] ?? false;
  }

  /** True for the one ticked box of a type whose other channel is off. */
  locked(type: NotificationType, channel: Channel): boolean {
    const preference = this.preference(type);
    if (!preference) {
      return false;
    }
    const other: Channel = channel === 'email' ? 'inApp' : 'email';
    return preference[channel] && !preference[other];
  }

  toggle(type: NotificationType, channel: Channel, value: boolean): void {
    this.message.set(null);
    this.problem.set(null);
    this.current.update((list) =>
      list.map((preference) =>
        preference.type === type ? { ...preference, [channel]: value } : preference,
      ),
    );
  }

  save(): void {
    if (!this.dirty() || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.message.set(null);
    this.problem.set(null);
    this.api
      .savePreferences(this.changed())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (preferences) => {
          this.show(preferences);
          this.message.set('notifications.preferences.saved');
          this.saving.set(false);
        },
        error: (err: unknown) => {
          this.problem.set(
            problemType(err) === 'network'
              ? 'notifications.preferences.errors.network'
              : 'notifications.preferences.errors.failed',
          );
          this.saving.set(false);
        },
      });
  }

  private show(preferences: NotificationPreference[]): void {
    this.loaded.set(preferences);
    this.current.set(preferences);
  }
}
