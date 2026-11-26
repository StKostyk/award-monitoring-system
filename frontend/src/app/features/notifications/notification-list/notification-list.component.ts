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
import { MatPaginator, MatPaginatorIntl, PageEvent } from '@angular/material/paginator';
import { MatProgressBar } from '@angular/material/progress-bar';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Subscription } from 'rxjs';

import {
  AppNotification,
  NotificationApi,
  NotificationType,
} from '../../../core/notifications/notification.api';
import { NotificationFeed } from '../../../core/notifications/notification-feed';
import {
  NotificationText,
  notificationTarget,
} from '../../../core/notifications/notification-text';
import { UnreadCount } from '../../../core/notifications/unread-count';
import { EmptyStateComponent } from '../../../shared/empty-state/empty-state.component';
import { IconComponent } from '../../../shared/icons/icon.component';
import { IconName } from '../../../shared/icons/icons';
import { ListRowComponent, ListRowsDirective } from '../../../shared/layout/list-row.component';
import { PageHeaderComponent } from '../../../shared/layout/page-header.component';
import { PaneComponent } from '../../../shared/layout/pane.component';
import { positiveIdOf } from '../../../shared/layout/query-selection';
import { SplitComponent } from '../../../shared/layout/split.component';
import { SkeletonRowsComponent } from '../../../shared/skeleton/skeleton-rows.component';
import { TranslatedPaginatorIntl } from '../../../shared/translated-paginator-intl';

/** Notifications per page. */
export const PAGE_SIZE = 20;

/** Highest page index the API binds (a Java int). */
const MAX_PAGE = 2_147_483_647;

/** A group of notification types with a chip of its own. */
export type NotificationGroup = 'awards' | 'review' | 'roles';

/** What the inbox lists: everything, the unread ones or one group. */
export type InboxFilter = 'all' | 'unread' | NotificationGroup;

/** The types of each group; the list asks the API for them. */
export const NOTIFICATION_GROUPS: Readonly<Record<NotificationGroup, readonly NotificationType[]>> =
  {
    awards: [
      'AWARD_APPROVED',
      'AWARD_REJECTED',
      'AWARD_RETURNED',
      'AWARD_CORRECTED',
      'AWARDS_REMINDER',
    ],
    review: ['AWARD_ESCALATED', 'REVIEW_OVERDUE', 'REVIEW_TAKEN_OVER'],
    roles: ['ROLE_ASSIGNED', 'ROLE_REVOKED', 'DELEGATION_CREATED', 'DELEGATION_REVOKED'],
  };

const GROUPS: readonly NotificationGroup[] = ['awards', 'review', 'roles'];
const FILTERS: readonly InboxFilter[] = ['all', 'unread', ...GROUPS];
const GROUP_ICONS: Readonly<Record<NotificationGroup, IconName>> = {
  awards: 'award',
  review: 'clipboard-check',
  roles: 'shield',
};

/** Where a notification's pane button leads and the key of its label. */
interface PaneTarget {
  readonly link: unknown[];
  readonly label: string;
}

/**
 * The signed-in person's inbox: a page of notifications beside a reading pane. Choosing one reads it in the pane;
 * only the pane's button leaves the page. Below 960 px the chosen notification takes the whole width.
 */
@Component({
  selector: 'app-notification-list',
  imports: [
    MatButton,
    IconComponent,
    EmptyStateComponent,
    ListRowComponent,
    ListRowsDirective,
    MatPaginator,
    MatProgressBar,
    PageHeaderComponent,
    PaneComponent,
    SkeletonRowsComponent,
    SplitComponent,
    TranslocoPipe,
  ],
  providers: [{ provide: MatPaginatorIntl, useClass: TranslatedPaginatorIntl }],
  templateUrl: './notification-list.component.html',
  styleUrl: './notification-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationListComponent implements OnInit {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(NotificationApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly unread = inject(UnreadCount);
  protected readonly texts = inject(NotificationText);
  protected readonly feed = new NotificationFeed();
  protected readonly groups = GROUPS;
  protected readonly pageSize = PAGE_SIZE;
  protected readonly total = signal(0);
  protected readonly allTotal = signal(0);
  protected readonly pageIndex = signal(0);
  protected readonly filter = signal<InboxFilter>('all');
  protected readonly selectedId = signal<number | null>(null);
  protected readonly selected = computed(() => {
    const id = this.selectedId();
    return this.feed.items().find((item) => item.id === id) ?? null;
  });
  protected readonly paneIcon = computed(() => {
    const item = this.selected();
    return item ? this.iconOf(item) : 'bell';
  });
  protected readonly notFound = computed(
    () =>
      this.selectedId() !== null &&
      this.selected() === null &&
      !this.feed.loading() &&
      !this.feed.failed(),
  );

  private loadedOnce = false;
  private counting: Subscription | undefined;

  ngOnInit(): void {
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => this.read(params));
  }

  /** Loads the current page again, moving to the last page when this one is past the end. */
  load(): void {
    const filter = this.filter();
    const types = filter === 'all' || filter === 'unread' ? [] : NOTIFICATION_GROUPS[filter];
    this.feed.load(
      this.pageIndex(),
      PAGE_SIZE,
      filter === 'unread',
      (page) => {
        const last = Math.ceil(page.totalElements / PAGE_SIZE) - 1;
        if (page.content.length === 0 && last >= 0 && last < this.pageIndex()) {
          this.total.set(0);
          this.navigate(filter, last, true);
          return false;
        }
        this.total.set(page.totalElements);
        if (filter === 'all') {
          this.allTotal.set(page.totalElements);
        }
        return true;
      },
      types,
    );
    if (filter !== 'all') {
      this.countAll();
    }
  }

  markAllRead(): void {
    this.feed.markAllRead(() => this.load());
  }

  protected choose(filter: InboxFilter): void {
    this.navigate(filter, 0);
  }

  /** Reads the notification in the pane and marks it read; the page stays where it is. */
  protected open(item: AppNotification): void {
    this.feed.read(item);
    this.select(item.id);
  }

  protected select(id: number | null): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { selected: id },
      queryParamsHandling: 'merge',
    });
  }

  protected page(event: PageEvent): void {
    this.navigate(this.filter(), event.pageIndex);
  }

  protected iconOf(item: AppNotification): IconName {
    const group = GROUPS.find((name) => NOTIFICATION_GROUPS[name].includes(item.type));
    return group ? GROUP_ICONS[group] : 'bell';
  }

  protected targetOf(item: AppNotification): PaneTarget | null {
    const link = notificationTarget(item);
    if (link === null) {
      return null;
    }
    const path = String(link[0]).slice(1);
    const label = path === 'awards' && link.length > 1 ? 'award' : path;
    return { link, label: `notifications.inbox.targets.${label}` };
  }

  protected go(target: PaneTarget): void {
    void this.router.navigate(target.link);
  }

  protected emptyText(): string {
    const filter = this.filter();
    if (filter === 'all') {
      return 'notifications.empty';
    }
    return filter === 'unread' ? 'notifications.emptyUnread' : 'notifications.inbox.emptyGroup';
  }

  private countAll(): void {
    this.counting?.unsubscribe();
    this.counting = this.api
      .list(0, 1, false, [])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (page) => this.allTotal.set(page.totalElements), error: () => undefined });
  }

  private read(params: ParamMap): void {
    const page = Number(params.get('page'));
    const filter = params.get('filter') as InboxFilter | null;
    const nextFilter = filter && FILTERS.includes(filter) ? filter : 'all';
    const nextPage = Number.isInteger(page) && page > 0 ? Math.min(page, MAX_PAGE) : 0;
    const selected = positiveIdOf(params.get('selected'));
    const onlySelected =
      this.loadedOnce &&
      selected !== null &&
      selected !== this.selectedId() &&
      nextFilter === this.filter() &&
      nextPage === this.pageIndex();
    this.selectedId.set(selected);
    if (onlySelected) {
      return;
    }
    this.loadedOnce = true;
    this.filter.set(nextFilter);
    this.pageIndex.set(nextPage);
    this.load();
  }

  private navigate(filter: InboxFilter, page: number, replaceUrl = false): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        filter: filter === 'all' ? null : filter,
        page: page > 0 ? page : null,
        selected: null,
      },
      replaceUrl,
    });
  }
}
