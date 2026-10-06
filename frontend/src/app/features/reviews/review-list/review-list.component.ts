import { BreakpointObserver } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatChip } from '@angular/material/chips';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatPaginator, MatPaginatorIntl, PageEvent } from '@angular/material/paginator';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatOption, MatSelect } from '@angular/material/select';
import {
  MatCell,
  MatCellDef,
  MatColumnDef,
  MatHeaderCell,
  MatHeaderCellDef,
  MatHeaderRow,
  MatHeaderRowDef,
  MatNoDataRow,
  MatRow,
  MatRowDef,
  MatTable,
} from '@angular/material/table';
import { MatTabLink, MatTabNav, MatTabNavPanel } from '@angular/material/tabs';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Store } from '@ngrx/store';
import { forkJoin, map } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { approvalScopes, reviewableLevels } from '../../../core/auth/permissions';
import { LanguageService } from '../../../core/i18n/language.service';
import { WIDE_LAYOUT } from '../../../core/layout/shell.component';
import {
  OrganizationSummary,
  OrganizationsService,
} from '../../../core/organizations/organizations.service';
import { kyivDate } from '../../../shared/date-format';
import { organizationName } from '../../../shared/organization-name';
import { TranslatedPaginatorIntl } from '../../../shared/translated-paginator-intl';
import { ApprovalLevel, awardTitle } from '../../awards/awards.service';
import { ReviewAssignment, ReviewFilters, ReviewItem } from '../reviews.service';
import { ReviewsActions } from '../store/reviews.actions';
import { reviewsFeature } from '../store/reviews.feature';

const PAGE_SIZES = [20, 50, 100];

/** The tabs of the queue, by the `assigned` filter they set. */
const TABS: { assigned: ReviewAssignment | null; label: string }[] = [
  { assigned: 'me', label: 'reviews.tabs.mine' },
  { assigned: 'unassigned', label: 'reviews.tabs.unassigned' },
  { assigned: null, label: 'reviews.tabs.all' },
];

@Component({
  selector: 'app-review-list',
  imports: [
    FormsModule,
    RouterLink,
    MatChip,
    MatFormField,
    MatLabel,
    MatSelect,
    MatOption,
    MatProgressBar,
    MatPaginator,
    MatTabNav,
    MatTabLink,
    MatTabNavPanel,
    MatTable,
    MatColumnDef,
    MatHeaderCell,
    MatHeaderCellDef,
    MatCell,
    MatCellDef,
    MatHeaderRow,
    MatHeaderRowDef,
    MatRow,
    MatRowDef,
    MatNoDataRow,
    TranslocoPipe,
  ],
  providers: [{ provide: MatPaginatorIntl, useClass: TranslatedPaginatorIntl }],
  templateUrl: './review-list.component.html',
  styleUrl: './review-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReviewListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly organizationList = inject(OrganizationsService);
  private readonly language = inject(LanguageService);

  protected readonly tabs = TABS;
  protected readonly levels = reviewableLevels(this.auth.permissions()) as ApprovalLevel[];
  protected readonly pageSizes = PAGE_SIZES;
  protected readonly columns = [
    'title',
    'recipient',
    'organization',
    'category',
    'level',
    'submitted',
    'deadline',
    'reviewer',
  ];
  protected readonly units = signal<OrganizationSummary[]>([]);
  protected readonly wide = toSignal(
    inject(BreakpointObserver)
      .observe(WIDE_LAYOUT)
      .pipe(map((state) => state.matches)),
    { initialValue: true },
  );

  readonly items = this.store.selectSignal(reviewsFeature.selectItems);
  readonly filters = this.store.selectSignal(reviewsFeature.selectFilters);
  readonly loading = this.store.selectSignal(reviewsFeature.selectLoading);
  readonly problem = this.store.selectSignal(reviewsFeature.selectProblem);
  readonly total = this.store.selectSignal(reviewsFeature.selectTotal);
  readonly pageIndex = this.store.selectSignal(reviewsFeature.selectPageIndex);
  readonly pageSize = this.store.selectSignal(reviewsFeature.selectPageSize);
  /** A message handed over by the page the reviewer came from. */
  readonly notice = signal<string | null>(null);

  ngOnInit(): void {
    this.notice.set((history.state as { notice?: string } | null)?.notice ?? null);
    this.store.dispatch(ReviewsActions.opened());
    forkJoin([
      this.organizationList.ofType('FACULTY'),
      this.organizationList.ofType('DEPARTMENT'),
    ]).subscribe({
      next: (lists) => this.units.set(this.inScope(lists.flat())),
      error: () => this.units.set([]),
    });
  }

  filter(change: Partial<ReviewFilters>): void {
    this.store.dispatch(
      ReviewsActions.filtersChanged({ filters: { ...this.filters(), ...change } }),
    );
  }

  page(event: PageEvent): void {
    this.store.dispatch(
      ReviewsActions.pageChanged({ pageIndex: event.pageIndex, pageSize: event.pageSize }),
    );
  }

  open(item: ReviewItem): void {
    void this.router.navigate(['/awards', item.awardId]);
  }

  title(item: ReviewItem): string {
    return awardTitle(item, this.language.current());
  }

  recipient(item: ReviewItem): string {
    const unit = item.recipient.organization;
    return unit ? organizationName(unit, this.language.current()) : item.owner.name;
  }

  organization(item: ReviewItem): string {
    return item.organization ? organizationName(item.organization, this.language.current()) : '—';
  }

  unitName(unit: OrganizationSummary): string {
    return organizationName(unit, this.language.current());
  }

  day(value: string | null): string {
    return value ? kyivDate(value, this.language.current()) : '—';
  }

  /**
   * The faculties and departments inside the caller's approval scopes; a scope that is neither, the university,
   * covers them all.
   */
  private inScope(organizations: OrganizationSummary[]): OrganizationSummary[] {
    const scopes = new Set(approvalScopes(this.auth.permissions()).map((s) => s.organizationId));
    const known = new Set(organizations.map((organization) => organization.id));
    const everything = [...scopes].some((id) => !known.has(id));
    const language = this.language.current();
    return organizations
      .filter(
        (organization) =>
          everything ||
          scopes.has(organization.id) ||
          (organization.parent !== null && scopes.has(organization.parent.id)),
      )
      .sort((a, b) => organizationName(a, language).localeCompare(organizationName(b, language)));
  }
}
