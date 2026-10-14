import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import {
  DOCUMENT,
  DestroyRef,
  Injectable,
  InjectionToken,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { Observable, Subscription, of, tap } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { AppNotification, NotificationApi } from './notification.api';

/** How often a visible tab asks for the unread count. */
export const NOTIFICATION_POLL_MS = new InjectionToken<number>('NOTIFICATION_POLL_MS', {
  factory: () => 60_000,
});

const BADGE_LIMIT = 99;

/**
 * The unread count of the signed-in person, fetched at sign-in, on a timer while the tab is visible and when the tab
 * becomes visible again; a 401 stops the timer until the tab is shown again, the menu opens or the person signs in.
 */
@Injectable({ providedIn: 'root' })
export class UnreadCount {
  private readonly api = inject(NotificationApi);
  private readonly auth = inject(AuthService);
  private readonly document = inject(DOCUMENT);
  private readonly interval = inject(NOTIFICATION_POLL_MS);

  readonly count = signal(0);
  /** The badge text: empty at 0, `99+` above 99. */
  readonly badge = computed(() => {
    const count = this.count();
    if (count === 0) {
      return '';
    }
    return count > BADGE_LIMIT ? `${BADGE_LIMIT}+` : String(count);
  });

  private timer: ReturnType<typeof setTimeout> | undefined;
  private request: Subscription | undefined;
  private active = false;
  private readonly visibilityChanged = (): void => this.onVisibilityChange();

  constructor() {
    this.document.addEventListener('visibilitychange', this.visibilityChanged);
    inject(DestroyRef).onDestroy(() => {
      this.stop();
      this.document.removeEventListener('visibilitychange', this.visibilityChanged);
    });
    effect(() => {
      const signedIn = this.auth.isAuthenticated();
      untracked(() => (signedIn ? this.start() : this.stop()));
    });
  }

  /** Fetches the count now, unless signed out or hidden. */
  refresh(): void {
    if (!this.active || this.document.visibilityState === 'hidden') {
      return;
    }
    this.cancel();
    this.request = this.api.unreadCount().subscribe({
      next: (count) => {
        this.count.set(count);
        this.schedule();
      },
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === HttpStatusCode.Unauthorized) {
          this.cancel();
        } else {
          this.schedule();
        }
      },
    });
  }

  /** Marks one notification read, sending nothing when it already is, then asks for the server's count. */
  markRead(notification: AppNotification): Observable<void> {
    if (notification.read) {
      return of(undefined);
    }
    return this.api.markRead(notification.id).pipe(
      tap(() => {
        this.count.update((count) => Math.max(0, count - 1));
        this.refresh();
      }),
    );
  }

  /** Marks every notification read and empties the badge. */
  markAllRead(): Observable<number> {
    return this.api.markAllRead().pipe(
      tap(() => {
        this.cancel();
        this.count.set(0);
        this.schedule();
      }),
    );
  }

  private start(): void {
    this.active = true;
    this.refresh();
  }

  private stop(): void {
    this.active = false;
    this.cancel();
    this.count.set(0);
  }

  private schedule(): void {
    clearTimeout(this.timer);
    if (this.active && this.document.visibilityState !== 'hidden') {
      this.timer = setTimeout(() => this.refresh(), this.interval);
    }
  }

  private cancel(): void {
    clearTimeout(this.timer);
    this.timer = undefined;
    this.request?.unsubscribe();
    this.request = undefined;
  }

  private onVisibilityChange(): void {
    if (this.document.visibilityState === 'hidden') {
      this.cancel();
    } else {
      this.refresh();
    }
  }
}
