import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatChip } from '@angular/material/chips';
import { DateAdapter } from '@angular/material/core';
import {
  MatDatepicker,
  MatDatepickerInput,
  MatDatepickerToggle,
} from '@angular/material/datepicker';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatPaginator, MatPaginatorIntl, PageEvent } from '@angular/material/paginator';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatOption, MatSelect } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Store } from '@ngrx/store';

import { AuthService } from '../../../core/auth/auth.service';
import { canCreateAwards } from '../../../core/auth/permissions';
import { LanguageService } from '../../../core/i18n/language.service';
import { kyivDate } from '../../../shared/date-format';
import { organizationName } from '../../../shared/organization-name';
import { TranslatedPaginatorIntl } from '../../../shared/translated-paginator-intl';
import {
  AWARD_STATUSES,
  Award,
  AwardFilters,
  AwardsService,
  CategoryNode,
  UnitRef,
  awardTitle,
  categoryName,
  flattenCategories,
} from '../awards.service';
import { RequestTimingComponent } from '../request-timing/request-timing.component';
import { AwardsActions } from '../store/awards.actions';
import { awardsFeature } from '../store/awards.feature';

const PAGE_SIZES = [20, 50, 100];

@Component({
  selector: 'app-award-list',
  imports: [
    RequestTimingComponent,
    FormsModule,
    RouterLink,
    MatButton,
    MatChip,
    MatFormField,
    MatLabel,
    MatInput,
    MatDatepicker,
    MatDatepickerInput,
    MatDatepickerToggle,
    MatSuffix,
    MatSelect,
    MatOption,
    MatProgressBar,
    MatPaginator,
    TranslocoPipe,
  ],
  providers: [{ provide: MatPaginatorIntl, useClass: TranslatedPaginatorIntl }],
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

  protected readonly statuses = AWARD_STATUSES;
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

  ngOnInit(): void {
    this.notice.set((history.state as { notice?: string } | null)?.notice ?? null);
    this.store.dispatch(AwardsActions.opened());
    this.service.categories().subscribe({
      next: (tree) => this.categories.set(flattenCategories(tree)),
      error: () => this.categories.set([]),
    });
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

  day(value: string): string {
    return kyivDate(value, this.language.current());
  }

  link(award: Award): (string | number)[] {
    return award.status === 'DRAFT' ? ['/awards', award.id, 'edit'] : ['/awards', award.id];
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
