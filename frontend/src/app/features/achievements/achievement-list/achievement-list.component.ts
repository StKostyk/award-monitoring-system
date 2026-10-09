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
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatPaginator, MatPaginatorIntl, PageEvent } from '@angular/material/paginator';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatOption, MatSelect, MatSelectTrigger } from '@angular/material/select';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Observable, catchError, map, of, switchMap, tap } from 'rxjs';

import { ReadProblem, readProblem } from '../../../core/api/problem';
import { LanguageService } from '../../../core/i18n/language.service';
import { kyivToday } from '../../../shared/date-format';
import { organizationName } from '../../../shared/organization-name';
import { TranslatedPaginatorIntl } from '../../../shared/translated-paginator-intl';
import { MAX_AGE_YEARS, Page } from '../../awards/awards.service';
import { AchievementCardComponent } from '../achievement-card/achievement-card.component';
import {
  Achievement,
  AchievementFilters,
  AchievementQuery,
  AchievementsService,
  NO_ACHIEVEMENT_FILTERS,
  PAGE_SIZES,
  RECIPIENT_TYPES,
  RECOGNITION_LEVELS,
  UnitOption,
  readQuery,
  writeQuery,
} from '../achievements.service';

@Component({
  selector: 'app-achievement-list',
  imports: [
    AchievementCardComponent,
    MatButton,
    MatFormField,
    MatLabel,
    MatSelect,
    MatSelectTrigger,
    MatOption,
    MatProgressBar,
    MatPaginator,
    TranslocoPipe,
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
  private readonly destroyRef = inject(DestroyRef);

  protected readonly levels = RECOGNITION_LEVELS;
  protected readonly recipients = RECIPIENT_TYPES;
  protected readonly pageSizes = PAGE_SIZES;
  protected readonly years = recentYears();

  readonly query = signal<AchievementQuery>({ filters: NO_ACHIEVEMENT_FILTERS, page: 0, size: 20 });
  readonly achievements = signal<Achievement[]>([]);
  readonly total = signal(0);
  readonly loading = signal(false);
  readonly problem = signal<ReadProblem | null>(null);
  readonly units = signal<UnitOption[]>([]);

  ngOnInit(): void {
    this.route.queryParamMap
      .pipe(
        map(readQuery),
        tap((query) => this.query.set(query)),
        switchMap((query) => this.fetch(query)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((page) => this.show(page));
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
    this.fetch(this.query()).subscribe((page) => this.show(page));
  }

  unitName(option: UnitOption): string {
    return organizationName(option.unit, this.language.current());
  }

  private fetch(query: AchievementQuery): Observable<Page<Achievement> | null> {
    this.loading.set(true);
    this.problem.set(null);
    return this.service.list(query).pipe(
      tap(() => this.loading.set(false)),
      catchError((error: unknown) => {
        this.loading.set(false);
        this.achievements.set([]);
        this.total.set(0);
        this.problem.set(readProblem(error));
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
    void this.router.navigate([], { relativeTo: this.route, queryParams: writeQuery(query) });
  }
}

/** This year and the years an award may date from, newest first. */
function recentYears(): number[] {
  const year = Number(kyivToday().substring(0, 4));
  return Array.from({ length: MAX_AGE_YEARS + 1 }, (_, index) => year - index);
}
