import { BreakpointObserver } from '@angular/cdk/layout';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { DateAdapter } from '@angular/material/core';
import {
  MatDatepicker,
  MatDatepickerInput,
  MatDatepickerIntl,
  MatDatepickerToggle,
} from '@angular/material/datepicker';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatPaginator, MatPaginatorIntl, PageEvent } from '@angular/material/paginator';
import { MatOption, MatSelect } from '@angular/material/select';
import { ActivatedRoute, ParamMap, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Store } from '@ngrx/store';
import {
  asyncScheduler,
  catchError,
  distinctUntilChanged,
  map,
  of,
  subscribeOn,
  switchMap,
} from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { canCreateAwards } from '../../../core/auth/permissions';
import { LanguageService } from '../../../core/i18n/language.service';
import { EmptyStateComponent } from '../../../shared/empty-state/empty-state.component';
import { StaggerDirective } from '../../../shared/effects/stagger.directive';
import { IconComponent } from '../../../shared/icons/icon.component';
import { KyivDatePipe } from '../../../shared/kyiv-date.pipe';
import { organizationName } from '../../../shared/organization-name';
import { SearchFieldComponent } from '../../../shared/search-field/search-field.component';
import { SkeletonRowsComponent } from '../../../shared/skeleton/skeleton-rows.component';
import { TranslatedDatepickerIntl } from '../../../shared/translated-datepicker-intl';
import { NARROW_SCREEN, narrowScreen } from '../../../shared/touch';
import { TranslatedPaginatorIntl } from '../../../shared/translated-paginator-intl';
import {
  AWARD_STATUSES,
  Award,
  AwardFilters,
  AwardStatus,
  AwardVisibility,
  AwardsService,
  CategoryNode,
  NO_FILTERS,
  UnitRef,
  VISIBILITIES,
  awardTitle,
  categoryName,
  flattenCategories,
} from '../awards.service';
import { RequestTimingComponent } from '../request-timing/request-timing.component';
import { AwardsActions } from '../store/awards.actions';
import { awardsFeature } from '../store/awards.feature';

const PAGE_SIZES = [10, 20, 50, 100];

@Component({
  selector: 'app-award-list',
  imports: [
    RequestTimingComponent,
    SearchFieldComponent,
    FormsModule,
    RouterLink,
    MatButton,
    IconComponent,
    MatFormField,
    MatLabel,
    MatInput,
    MatDatepicker,
    MatDatepickerInput,
    MatDatepickerToggle,
    MatSuffix,
    MatSelect,
    MatOption,
    EmptyStateComponent,
    SkeletonRowsComponent,
    StaggerDirective,
    MatPaginator,
    TranslocoPipe,
    KyivDatePipe,
  ],
  providers: [
    { provide: MatDatepickerIntl, useClass: TranslatedDatepickerIntl },
    { provide: MatPaginatorIntl, useClass: TranslatedPaginatorIntl },
  ],
  templateUrl: './award-list.component.html',
  styleUrl: './award-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AwardListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly auth = inject(AuthService);
  private readonly service = inject(AwardsService);
  private readonly language = inject(LanguageService);
  private readonly dates = inject<DateAdapter<string>>(DateAdapter);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);

  protected readonly statuses = AWARD_STATUSES;
  protected readonly visibilities = VISIBILITIES;
  protected readonly canCreate = canCreateAwards(this.auth.permissions());
  protected readonly categories = signal<{ category: CategoryNode; depth: number }[]>([]);

  readonly awards = this.store.selectSignal(awardsFeature.selectAwards);
  readonly filters = this.store.selectSignal(awardsFeature.selectFilters);
  readonly loading = this.store.selectSignal(awardsFeature.selectLoading);
  readonly problem = this.store.selectSignal(awardsFeature.selectProblem);
  readonly total = this.store.selectSignal(awardsFeature.selectTotal);
  readonly pageIndex = this.store.selectSignal(awardsFeature.selectPageIndex);
  readonly pageSize = this.store.selectSignal(awardsFeature.selectPageSize);
  protected readonly pageSizes = PAGE_SIZES;
  readonly notice = signal<string | null>(null);
  protected readonly narrow = toSignal(
    inject(BreakpointObserver)
      .observe(NARROW_SCREEN)
      .pipe(map((state) => state.matches)),
    { initialValue: narrowScreen() },
  );
  protected readonly filtersOpen = signal(false);
  /** Awards per status under the other filters; null while unknown. */
  protected readonly counts = signal<Record<AwardStatus, number> | null>(null);
  protected readonly filtered = computed(() =>
    Object.values(this.filters()).some((value) => value !== null),
  );
  protected readonly activeFilters = computed(() => {
    const { status, category, dateFrom, dateTo, visibility } = this.filters();
    return [status, category, dateFrom, dateTo, visibility].filter((value) => value !== null)
      .length;
  });

  ngOnInit(): void {
    this.notice.set((history.state as { notice?: string } | null)?.notice ?? null);
    const linked = linkedFilters(this.route.snapshot.queryParamMap);
    this.store.dispatch(
      linked ? AwardsActions.filtersChanged({ filters: linked }) : AwardsActions.opened(),
    );
    this.service
      .categories()
      .pipe(subscribeOn(asyncScheduler), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (tree) => this.categories.set(flattenCategories(tree)),
        error: () => this.categories.set([]),
      });
    this.store
      .select(awardsFeature.selectFilters)
      .pipe(
        map((filters) => ({ ...filters, status: null })),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        switchMap((filters) => this.service.statusCounts(filters).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((counts) => this.counts.set(counts));
  }

  /** The number of awards of every status together. */
  allCount(counts: Record<AwardStatus, number>): number {
    return Object.values(counts).reduce((sum, count) => sum + count, 0);
  }

  clearFilters(): void {
    this.store.dispatch(AwardsActions.filtersChanged({ filters: NO_FILTERS }));
  }

  filter(change: Partial<AwardFilters>): void {
    this.store.dispatch(
      AwardsActions.filtersChanged({ filters: { ...this.filters(), ...change } }),
    );
  }

  page(event: PageEvent): void {
    this.store.dispatch(
      AwardsActions.pageChanged({ pageIndex: event.pageIndex, pageSize: event.pageSize }),
    );
  }

  validDate(value: string | null): string | null {
    return value !== null && this.dates.isValid(value) ? value : null;
  }

  link(award: Award): (string | number)[] {
    return award.status === 'DRAFT' ? ['/awards', award.id, 'edit'] : ['/awards', award.id];
  }

  /** Whom an approved personal award is shown to besides its owner and reviewers; null when nobody. */
  sharedWith(award: Award): AwardVisibility | null {
    return award.status === 'APPROVED' && award.visibility !== 'PRIVATE' ? award.visibility : null;
  }

  title(award: Award): string {
    return awardTitle(award, this.language.current());
  }

  category(award: Award): string {
    return award.category ? categoryName(award.category, this.language.current()) : '';
  }

  unitName(unit: UnitRef): string {
    return organizationName(unit, this.language.current());
  }

  optionName(category: CategoryNode): string {
    return categoryName(category, this.language.current());
  }
}

/**
 * The filters a link to the list carries in its address, such as the dashboard's counts by status or visibility.
 *
 * @param params the query of the address
 * @return the filters with the known status and visibility, or null when the address carries neither
 */
export function linkedFilters(params: ParamMap): AwardFilters | null {
  const status = AWARD_STATUSES.find((value) => value === params.get('status')) ?? null;
  const visibility = VISIBILITIES.find((value) => value === params.get('visibility')) ?? null;
  return status === null && visibility === null ? null : { ...NO_FILTERS, status, visibility };
}
