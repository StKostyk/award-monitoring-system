import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
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

import { NotificationFeed } from '../../../core/notifications/notification-feed';
import { NotificationText } from '../../../core/notifications/notification-text';
import { UnreadCount } from '../../../core/notifications/unread-count';
import { TranslatedPaginatorIntl } from '../../../shared/translated-paginator-intl';

/** Notifications per page. */
export const PAGE_SIZE = 20;

/** Highest page index the API binds (a Java int). */
const MAX_PAGE = 2_147_483_647;

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
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly unread = inject(UnreadCount);
  protected readonly texts = inject(NotificationText);
  protected readonly feed = new NotificationFeed();
  protected readonly pageSize = PAGE_SIZE;
  protected readonly total = signal(0);
  protected readonly pageIndex = signal(0);
  protected readonly unreadOnly = signal(false);

  ngOnInit(): void {
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => this.read(params));
  }

  /** Loads the current page again, moving to the last page when this one is past the end. */
  load(): void {
    this.feed.load(this.pageIndex(), PAGE_SIZE, this.unreadOnly(), (page) => {
      const last = Math.ceil(page.totalElements / PAGE_SIZE) - 1;
      if (page.content.length === 0 && last >= 0 && last < this.pageIndex()) {
        this.total.set(0);
        this.navigate(this.unreadOnly(), last, true);
        return false;
      }
      this.total.set(page.totalElements);
      return true;
    });
  }

  markAllRead(): void {
    this.feed.markAllRead(() => this.load());
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
    this.pageIndex.set(Number.isInteger(page) && page > 0 ? Math.min(page, MAX_PAGE) : 0);
    this.load();
  }

  private navigate(unreadOnly: boolean, page: number, replaceUrl = false): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { unread: unreadOnly ? true : null, page: page > 0 ? page : null },
      replaceUrl,
    });
  }
}
