import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatPaginator, MatPaginatorIntl, PageEvent } from '@angular/material/paginator';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatSlideToggle, MatSlideToggleChange } from '@angular/material/slide-toggle';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Subscription } from 'rxjs';

import { AppNotification, NotificationApi } from '../../../core/notifications/notification.api';
import {
  NotificationText,
  notificationTarget,
} from '../../../core/notifications/notification-text';
import { UnreadCount } from '../../../core/notifications/unread-count';
import { TranslatedPaginatorIntl } from '../../../shared/translated-paginator-intl';

/** Notifications per page. */
export const PAGE_SIZE = 20;

/** Every notification of the signed-in person, a page at a time, optionally unread only. */
@Component({
  selector: 'app-notification-list',
  imports: [MatButton, MatIcon, MatPaginator, MatProgressBar, MatSlideToggle, TranslocoPipe],
  providers: [{ provide: MatPaginatorIntl, useClass: TranslatedPaginatorIntl }],
  templateUrl: './notification-list.component.html',
  styleUrl: './notification-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationListComponent implements OnInit {
  private readonly api = inject(NotificationApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly unread = inject(UnreadCount);
  protected readonly texts = inject(NotificationText);
  protected readonly pageSize = PAGE_SIZE;
  protected readonly items = signal<AppNotification[]>([]);
  protected readonly total = signal(0);
  protected readonly pageIndex = signal(0);
  protected readonly unreadOnly = signal(false);
  protected readonly loading = signal(false);
  protected readonly failed = signal(false);
  protected readonly actionFailed = signal(false);
  protected readonly anyUnread = computed(
    () => this.unread.count() > 0 || this.items().some((item) => !item.read),
  );

  private request: Subscription | undefined;

  ngOnInit(): void {
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => this.read(params));
  }

  /** Loads the current page again. */
  load(): void {
    this.request?.unsubscribe();
    this.loading.set(true);
    this.failed.set(false);
    this.request = this.api.list(this.pageIndex(), PAGE_SIZE, this.unreadOnly()).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.total.set(page.totalElements);
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
      next: () =>
        this.items.update((items) =>
          items.map((item) => (item.id === notification.id ? { ...item, read: true } : item)),
        ),
      error: () => undefined,
    });
    const target = notificationTarget(notification);
    if (target) {
      void this.router.navigate(target);
    }
  }

  markAllRead(): void {
    this.actionFailed.set(false);
    this.unread.markAllRead().subscribe({
      next: () => this.load(),
      error: () => this.actionFailed.set(true),
    });
  }

  protected filter(change: MatSlideToggleChange): void {
    this.navigate(change.checked, 0);
  }

  protected page(event: PageEvent): void {
    this.navigate(this.unreadOnly(), event.pageIndex);
  }

  private read(params: ParamMap): void {
    const page = Number(params.get('page'));
    this.unreadOnly.set(params.get('unread') === 'true');
    this.pageIndex.set(Number.isInteger(page) && page > 0 ? page : 0);
    this.load();
  }

  private navigate(unreadOnly: boolean, page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { unread: unreadOnly ? true : null, page: page > 0 ? page : null },
    });
  }
}
