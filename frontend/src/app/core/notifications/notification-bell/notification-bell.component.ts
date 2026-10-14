import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Subscription } from 'rxjs';

import { AppNotification, NotificationApi } from '../notification.api';
import { NotificationText, notificationTarget } from '../notification-text';
import { UnreadCount } from '../unread-count';

/** How many notifications the toolbar menu lists. */
export const MENU_SIZE = 10;

/** The toolbar bell: the unread badge and a menu of the latest notifications. */
@Component({
  selector: 'app-notification-bell',
  imports: [
    MatIcon,
    MatIconButton,
    MatMenu,
    MatMenuItem,
    MatMenuTrigger,
    RouterLink,
    TranslocoPipe,
  ],
  templateUrl: './notification-bell.component.html',
  styleUrl: './notification-bell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationBellComponent {
  private readonly api = inject(NotificationApi);
  private readonly router = inject(Router);

  protected readonly unread = inject(UnreadCount);
  protected readonly texts = inject(NotificationText);
  protected readonly items = signal<AppNotification[]>([]);
  protected readonly loading = signal(false);
  protected readonly failed = signal(false);
  protected readonly anyUnread = computed(
    () => this.unread.count() > 0 || this.items().some((item) => !item.read),
  );

  private request: Subscription | undefined;

  /** Loads the latest notifications when the menu opens. */
  load(): void {
    this.unread.refresh();
    this.request?.unsubscribe();
    this.loading.set(true);
    this.failed.set(false);
    this.request = this.api.list(0, MENU_SIZE).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.failed.set(true);
      },
    });
  }

  /** Marks the item read when it is unread and opens its target. */
  open(notification: AppNotification): void {
    this.unread.markRead(notification).subscribe({
      next: () => this.markLocally((item) => item.id === notification.id),
      error: () => undefined,
    });
    const target = notificationTarget(notification);
    if (target) {
      void this.router.navigate(target);
    }
  }

  markAllRead(): void {
    this.unread.markAllRead().subscribe({
      next: () => this.markLocally(() => true),
      error: () => undefined,
    });
  }

  private markLocally(matches: (item: AppNotification) => boolean): void {
    this.items.update((items) =>
      items.map((item) => (matches(item) ? { ...item, read: true } : item)),
    );
  }
}
