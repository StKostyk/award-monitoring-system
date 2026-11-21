import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatPaginator, MatPaginatorIntl, PageEvent } from '@angular/material/paginator';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatOption, MatSelect, MatSelectTrigger } from '@angular/material/select';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import {
  Observable,
  Subject,
  catchError,
  combineLatest,
  distinctUntilChanged,
  filter,
  map,
  of,
  startWith,
  switchMap,
  tap,
} from 'rxjs';

import { ReadProblem, readProblem } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { currentKyivYear, kyivToday } from '../../../shared/date-format';
import { EmptyStateComponent } from '../../../shared/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../../shared/layout/page-header.component';
import { PaneComponent } from '../../../shared/layout/pane.component';
import { YearGroupComponent } from '../../../shared/layout/year-group.component';
import { organizationName } from '../../../shared/organization-name';
import { pluralForm } from '../../../shared/plural';
import { SearchFieldComponent } from '../../../shared/search-field/search-field.component';
import { TranslatedPaginatorIntl } from '../../../shared/translated-paginator-intl';
import { MAX_AGE_YEARS, Page, RECOGNITION_LEVELS } from '../../awards/awards.service';
import { AchievementCardComponent } from '../achievement-card/achievement-card.component';
import {
  Achievement,
  AchievementFilters,
  AchievementQuery,
  AchievementScope,
  AchievementsService,
  NO_ACHIEVEMENT_FILTERS,
  PAGE_SIZES,
  RECIPIENT_TYPES,
  UnitOption,
  readQuery,
  writeQuery,
} from '../achievements.service';
import { groupByYear, pickHighlight } from '../highlight';
import { UnitHeaderComponent } from '../unit-header/unit-header.component';

/** A link to the same list on the other side: the public page, or the page for staff. */
export interface CounterpartLink {
  label: 'publicPage' | 'viewAsStaff';
  path: (string | number)[];
  queryParams: Params;
}

const UNIT_ID = /^\d{1,9}$/;

@Component({
  selector: 'app-achievement-list',
  imports: [
    AchievementCardComponent,
    EmptyStateComponent,
    MatButton,
    MatFormField,
    MatLabel,
    MatSelect,
    MatSelectTrigger,
    MatOption,
    MatProgressBar,
    MatPaginator,
    PageHeaderComponent,
    PaneComponent,
    RouterLink,
    SearchFieldComponent,
    TranslocoPipe,
    UnitHeaderComponent,
    YearGroupComponent,
  ],
  providers: [{ provide: MatPaginatorIntl, useClass: TranslatedPaginatorIntl }],
  templateUrl: './achievement-list.component.html',
  styleUrl: './achievement-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AchievementListComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(AchievementsService);
  private readonly language = inject(LanguageService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly retries = new Subject<void>();

  protected readonly levels = [...RECOGNITION_LEVELS].reverse();
  protected readonly recipients = RECIPIENT_TYPES;
  protected readonly pageSizes = PAGE_SIZES;
  protected readonly years = recentYears();
  /** The awards shared with colleagues, or the public ones, as the route says. */
  protected readonly scope: AchievementScope =
    this.route.snapshot.data['scope'] === 'public' ? 'public' : 'signed-in';
  protected readonly unitPages = this.scope === 'public' ? '/public/units' : '/units';

  readonly query = signal<AchievementQuery>({ filters: NO_ACHIEVEMENT_FILTERS, page: 0, size: 20 });
  readonly achievements = signal<Achievement[]>([]);
  readonly total = signal(0);
  readonly loading = signal(false);
  readonly problem = signal<ReadProblem | null>(null);
  readonly units = signal<UnitOption[]>([]);
  /** The unit of a unit page, which the unit filter cannot change. */
  readonly fixedUnit = signal<number | null>(null);
  private readonly candidates = signal<Achievement[]>([]);
  /**
   * The achievement of the current Kyiv month with the highest level, for the list's unit and recipients; only on
   * the unfiltered feed, so that it never contradicts a search, level or year the list is narrowed to.
   */
  readonly highlight = computed(() => {
    const { q, level, year } = this.query().filters;
    return q !== null || level !== null || year !== null || this.problem() !== null
      ? null
      : pickHighlight(this.candidates(), kyivToday().slice(0, 7));
  });
  protected readonly groups = computed(() => groupByYear(this.achievements()));

  /** The name the listed awards carry for the unit of the page. */
  protected readonly unitFallback = computed(() => {
    const id = this.fixedUnit();
    const unit = this.achievements().find((item) => item.recipient.unit.id === id)?.recipient.unit;
    return unit ? organizationName(unit, this.language.current()) : null;
  });
  /** The person whose public awards the page lists, named as the listed awards carry it. */
  protected readonly personName = computed(() =>
    this.query().filters.person === null
      ? null
      : (this.achievements()[0]?.recipient.personName ?? null),
  );
  protected readonly counterpart = computed<CounterpartLink | null>(() => {
    const unit = this.fixedUnit();
    const query = this.withoutFixedUnit(this.query());
    const queryParams = writeQuery({ ...query, filters: { ...query.filters, person: null } });
    if (this.scope === 'signed-in') {
      const path =
        unit === null ? ['/public/achievements'] : ['/public/units', unit, 'achievements'];
      return { label: 'publicPage', path, queryParams };
    }
    if (!this.auth.isAuthenticated()) {
      return null;
    }
    const path = unit === null ? ['/achievements'] : ['/units', unit, 'achievements'];
    return { label: 'viewAsStaff', path, queryParams };
  });

  ngOnInit(): void {
    combineLatest([this.route.paramMap, this.route.queryParamMap])
      .pipe(
        map(([params, queryParams]) => ({ id: params.get('id'), query: readQuery(queryParams) })),
        filter(({ id }) => id === null || UNIT_ID.test(id) || this.notFound()),
        map(({ id, query }) => {
          const unit = id === null ? null : Number(id);
          this.fixedUnit.set(unit);
          const person = this.scope === 'public' ? query.filters.person : null;
          return {
            ...query,
            filters: { ...query.filters, unit: unit ?? query.filters.unit, person },
          };
        }),
        tap((query) => this.query.set(query)),
        switchMap((query) =>
          this.retries.pipe(
            startWith(undefined),
            switchMap(() => this.fetch(query)),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((page) => this.show(page));
    this.watchHighlight();
    this.service.units(this.language.current()).subscribe({
      next: (units) => this.units.set(units),
      error: () => this.units.set([]),
    });
  }

  /** Puts the changed filters into the address bar, back on the first page. */
  filter(change: Partial<AchievementFilters>): void {
    const query = this.query();
    this.navigate({ ...query, filters: { ...query.filters, ...change }, page: 0 });
  }

  page(event: PageEvent): void {
    this.navigate({ ...this.query(), page: event.pageIndex, size: event.pageSize });
  }

  /** Loads the same page again after a failure. */
  retry(): void {
    this.retries.next();
  }

  /** The plural form of a count, for the count keys. */
  plural(count: number): Intl.LDMLPluralRule {
    return pluralForm(count, this.language.current());
  }

  unitName(option: UnitOption): string {
    return organizationName(option.unit, this.language.current());
  }

  private watchHighlight(): void {
    const filters$ = toObservable(this.query, { injector: this.injector }).pipe(
      map(({ filters }) => filters),
      distinctUntilChanged(
        (a, b) => a.unit === b.unit && a.recipient === b.recipient && a.person === b.person,
      ),
    );
    combineLatest([filters$, this.retries.pipe(startWith(undefined))])
      .pipe(
        switchMap(([filters]) =>
          this.service.highlightCandidates(filters, this.scope).pipe(catchError(() => of([]))),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((items) => this.candidates.set(items));
  }

  private fetch(query: AchievementQuery): Observable<Page<Achievement> | null> {
    this.loading.set(true);
    this.problem.set(null);
    return this.service.list(query, this.scope).pipe(
      tap(() => this.loading.set(false)),
      catchError((error: unknown) => {
        const problem = readProblem(error);
        this.loading.set(false);
        this.achievements.set([]);
        this.total.set(0);
        this.problem.set(problem);
        if (problem === 'gone' && this.fixedUnit() !== null) {
          this.notFound();
        }
        return of(null);
      }),
    );
  }

  private show(page: Page<Achievement> | null): void {
    if (page) {
      this.achievements.set(page.content);
      this.total.set(page.totalElements);
    }
  }

  private navigate(query: AchievementQuery): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: writeQuery(this.withoutFixedUnit(query)),
    });
  }

  private withoutFixedUnit(query: AchievementQuery): AchievementQuery {
    return this.fixedUnit() === null
      ? query
      : { ...query, filters: { ...query.filters, unit: null } };
  }

  /** Shows the not-found page under the address that was asked for; false, so a filter can drop the request. */
  private notFound(): false {
    void this.router.navigate(['/not-found'], { skipLocationChange: true });
    return false;
  }
}

/** This year and the years an award may date from, newest first. */
function recentYears(): number[] {
  const year = currentKyivYear();
  return Array.from({ length: MAX_AGE_YEARS + 1 }, (_, index) => year - index);
}
