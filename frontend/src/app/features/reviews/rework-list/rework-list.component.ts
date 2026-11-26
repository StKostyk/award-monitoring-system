import { BreakpointObserver } from '@angular/cdk/layout';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  output,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatPaginator, MatPaginatorIntl, PageEvent } from '@angular/material/paginator';
import { MatProgressBar } from '@angular/material/progress-bar';
import { TranslocoPipe } from '@jsverse/transloco';
import { map } from 'rxjs';

import { LanguageService } from '../../../core/i18n/language.service';
import { WIDE_LAYOUT } from '../../../core/layout/shell.component';
import { EmptyStateComponent } from '../../../shared/empty-state/empty-state.component';
import { KyivDatePipe } from '../../../shared/kyiv-date.pipe';
import { ListRowComponent, ListRowsDirective } from '../../../shared/layout/list-row.component';
import { PaneComponent } from '../../../shared/layout/pane.component';
import { querySelection } from '../../../shared/layout/query-selection';
import { SplitComponent } from '../../../shared/layout/split.component';
import { organizationName } from '../../../shared/organization-name';
import { TranslatedPaginatorIntl } from '../../../shared/translated-paginator-intl';
import { initials } from '../../../shared/widgets/top-widget.component';
import { awardTitle } from '../../awards/awards.service';
import { ReturnedItem, ReviewsService } from '../reviews.service';

const PAGE_SIZES = [20, 50, 100];

/**
 * The in-rework tab of the queue: awards sent back at the caller's levels and not submitted again, with who sent
 * them back, when and why. Read-only; the pane shows the chosen award from the page.
 */
@Component({
  selector: 'app-rework-list',
  imports: [
    MatButton,
    MatPaginator,
    MatProgressBar,
    EmptyStateComponent,
    KyivDatePipe,
    ListRowComponent,
    ListRowsDirective,
    PaneComponent,
    SplitComponent,
    TranslocoPipe,
  ],
  providers: [{ provide: MatPaginatorIntl, useClass: TranslatedPaginatorIntl }],
  templateUrl: './rework-list.component.html',
  styleUrl: './rework-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReworkListComponent implements OnInit {
  private readonly reviews = inject(ReviewsService);
  private readonly language = inject(LanguageService);
  private readonly shown = querySelection('returned');
  private readonly wide = toSignal(
    inject(BreakpointObserver)
      .observe(WIDE_LAYOUT)
      .pipe(map((state) => state.matches)),
    { initialValue: true },
  );

  /** The number of awards in the tab after every load. */
  readonly totalChange = output<number>();

  protected readonly pageSizes = PAGE_SIZES;
  readonly items = signal<ReturnedItem[]>([]);
  readonly total = signal(0);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(PAGE_SIZES[0]);
  readonly loading = signal(false);
  readonly failed = signal(false);
  /** The award chosen with `?returned=`, null when none is. */
  readonly selectedId = this.shown.id;
  /** The award in the pane: the chosen one, else the first of the page beside the list at 960 px and wider. */
  readonly current = computed<ReturnedItem | null>(() => {
    const id = this.selectedId();
    if (id !== null) {
      return this.items().find((item) => item.awardId === id) ?? null;
    }
    return this.wide() ? (this.items()[0] ?? null) : null;
  });

  ngOnInit(): void {
    this.load();
  }

  page(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.select(null);
    this.load();
  }

  select(awardId: number | null): void {
    this.shown.select(awardId);
  }

  isShown(item: ReturnedItem): boolean {
    return item.awardId === this.current()?.awardId;
  }

  title(item: ReturnedItem): string {
    return awardTitle(item, this.language.current());
  }

  recipient(item: ReturnedItem): string {
    const unit = item.recipient.organization;
    return unit ? organizationName(unit, this.language.current()) : item.owner.name;
  }

  unit(item: ReturnedItem): string | null {
    return item.organization ? organizationName(item.organization, this.language.current()) : null;
  }

  initialsOf(item: ReturnedItem): string {
    return initials(this.recipient(item));
  }

  /** Reads the current page again. */
  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.reviews.returned(this.pageIndex(), this.pageSize()).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.total.set(page.totalElements);
        this.loading.set(false);
        this.totalChange.emit(page.totalElements);
      },
      error: () => {
        this.items.set([]);
        this.loading.set(false);
        this.failed.set(true);
      },
    });
  }
}
