import { DestroyRef, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';

import { Page } from '../../shared/page';
import { AppNotification, NotificationApi } from './notification.api';
import { notificationTarget } from './notification-text';
import { UnreadCount } from './unread-count';

/**
 * The notifications one view shows: a page loaded from the API, its loading and error state, and the actions
 * on it. Create it in a component field so it lives and ends with the component.
 */
export class NotificationFeed {
  private readonly api = inject(NotificationApi);
  private readonly router = inject(Router);
  private readonly unread = inject(UnreadCount);

  readonly items = signal<AppNotification[]>([]);
  readonly loading = signal(false);
  readonly failed = signal(false);
  readonly actionFailed = signal(false);
  readonly anyUnread = computed(
    () => this.unread.count() > 0 || this.items().some((item) => !item.read),
  );

  private request: Subscription | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.request?.unsubscribe());
  }

  /**
   * Loads one page, dropping a request still running.
   *
   * @param accept sees the page first; returning false leaves the list empty and the feed loading, for a caller
   *   that moves on to another page
   */
  load(
    page: number,
    size: number,
    unreadOnly = false,
    accept: (page: Page<AppNotification>) => boolean = () => true,
  ): void {
    this.request?.unsubscribe();
    this.loading.set(true);
    this.failed.set(false);
    this.actionFailed.set(false);
    this.request = this.api.list(page, size, unreadOnly).subscribe({
      next: (result) => {
        if (!accept(result)) {
          this.items.set([]);
          return;
        }
        this.items.set(result.content);
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
    this.actionFailed.set(false);
    this.unread.markRead(notification).subscribe({
      next: () => this.markLocally((item) => item.id === notification.id),
      error: () => this.actionFailed.set(true),
    });
    const target = notificationTarget(notification);
    if (target) {
      void this.router.navigate(target);
    }
  }

  /**
   * Marks every notification read.
   *
   * @param done runs once the server confirmed; by default the shown items turn read
   */
  markAllRead(done: () => void = () => this.markLocally(() => true)): void {
    this.actionFailed.set(false);
    this.unread.markAllRead().subscribe({
      next: done,
      error: () => this.actionFailed.set(true),
    });
  }

  private markLocally(matches: (item: AppNotification) => boolean): void {
    this.items.update((items) =>
      items.map((item) => (matches(item) ? { ...item, read: true } : item)),
    );
  }
}
