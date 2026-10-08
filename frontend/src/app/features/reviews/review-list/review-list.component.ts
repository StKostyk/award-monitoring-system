import { BreakpointObserver } from '@angular/cdk/layout';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatChip } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
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
import { filter as present, forkJoin, map, switchMap } from 'rxjs';

import { problemType } from '../../../core/api/problem';
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
import {
  DecisionDialogComponent,
  DecisionDialogData,
  DecisionInput,
} from '../decision-dialog/decision-dialog.component';
import {
  BatchItemResult,
  DecisionType,
  ReviewAssignment,
  ReviewFilters,
  ReviewItem,
  ReviewsService,
} from '../reviews.service';
import { ReviewsActions } from '../store/reviews.actions';
import { reviewsFeature } from '../store/reviews.feature';

const PAGE_SIZES = [20, 50, 100];

/** Failure reasons the batch summary names; any other code reads as `unknown`. */
const REASONS = [
  'request-claimed',
  'request-stale',
  'request-closed',
  'no-higher-level',
  'validation-failed',
  'not-found',
  'try-again',
];

/** The outcome of the last batch: how many were decided and which awards failed, with the reason key. */
export interface BatchSummary {
  done: number;
  total: number;
  failed: { awardId: number; title: string; reason: string }[];
}

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
    MatButton,
    MatCheckbox,
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
  private readonly dialog = inject(MatDialog);
  private readonly reviews = inject(ReviewsService);

  protected readonly tabs = TABS;
  protected readonly levels = reviewableLevels(this.auth.permissions()) as ApprovalLevel[];
  protected readonly pageSizes = PAGE_SIZES;
  protected readonly columns = [
    'select',
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

  /** Award ids the reviewer ticked; only those on the current page count. */
  readonly selected = signal<ReadonlySet<number>>(new Set());
  readonly selection = computed(() =>
    this.items().filter((item) => this.selected().has(item.awardId)),
  );
  readonly allSelected = computed(
    () => this.items().length > 0 && this.selection().length === this.items().length,
  );
  /** «Передати декану» when every selected request waits at the faculty secretary. */
  readonly escalateTarget = computed<ApprovalLevel | null>(() =>
    this.selection().every((item) => item.level === 'FACULTY_SECRETARY') ? 'DEAN' : null,
  );
  readonly busy = signal(false);
  readonly summary = signal<BatchSummary | null>(null);
  readonly batchProblem = signal<string | null>(null);

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
    this.selected.set(new Set());
    this.store.dispatch(
      ReviewsActions.filtersChanged({ filters: { ...this.filters(), ...change } }),
    );
  }

  page(event: PageEvent): void {
    this.selected.set(new Set());
    this.store.dispatch(
      ReviewsActions.pageChanged({ pageIndex: event.pageIndex, pageSize: event.pageSize }),
    );
  }

  open(item: ReviewItem): void {
    void this.router.navigate(['/awards', item.awardId]);
  }

  isSelected(item: ReviewItem): boolean {
    return this.selected().has(item.awardId);
  }

  toggle(item: ReviewItem): void {
    const next = new Set(this.selected());
    if (!next.delete(item.awardId)) {
      next.add(item.awardId);
    }
    this.selected.set(next);
  }

  toggleAll(): void {
    this.selected.set(
      this.allSelected() ? new Set() : new Set(this.items().map((item) => item.awardId)),
    );
  }

  /** Confirms a decision for the selected awards and applies it to each of them. */
  decide(decision: DecisionType): void {
    const chosen = this.selection();
    if (chosen.length === 0 || this.busy()) {
      return;
    }
    const data: DecisionDialogData = {
      decision,
      target: decision === 'ESCALATE' ? this.escalateTarget() : null,
      documents: 0,
      count: chosen.length,
    };
    this.dialog
      .open<DecisionDialogComponent, DecisionDialogData, DecisionInput>(DecisionDialogComponent, {
        data,
        width: '480px',
      })
      .afterClosed()
      .pipe(
        present((input): input is DecisionInput => !!input),
        switchMap((input) => {
          this.busy.set(true);
          this.summary.set(null);
          this.batchProblem.set(null);
          return this.reviews.decideBatch({
            decision,
            ...input,
            items: chosen.map((item) => ({
              awardId: item.awardId,
              requestVersion: item.requestVersion,
            })),
          });
        }),
      )
      .subscribe({
        next: (results) => this.decided(chosen, results),
        error: (error: unknown) => this.batchFailed(error),
      });
  }

  clear(): void {
    this.selected.set(new Set());
  }

  dismiss(): void {
    this.summary.set(null);
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

  private decided(chosen: ReviewItem[], results: BatchItemResult[]): void {
    this.busy.set(false);
    const failed = results.filter((result) => result.outcome === 'FAILED');
    const titles = new Map(chosen.map((item) => [item.awardId, this.title(item)]));
    this.summary.set({
      done: results.length - failed.length,
      total: results.length,
      failed: failed.map((result) => ({
        awardId: result.awardId,
        title: titles.get(result.awardId) ?? String(result.awardId),
        reason: reasonOf(result),
      })),
    });
    this.selected.set(new Set(failed.map((result) => result.awardId)));
    this.store.dispatch(
      ReviewsActions.itemsDecided({
        awardIds: results
          .filter((result) => result.outcome === 'DONE')
          .map((result) => result.awardId),
      }),
    );
    if (failed.length > 0) {
      this.store.dispatch(
        ReviewsActions.pageChanged({ pageIndex: this.pageIndex(), pageSize: this.pageSize() }),
      );
    }
  }

  private batchFailed(error: unknown): void {
    this.busy.set(false);
    const type = problemType(error);
    this.batchProblem.set(type === 'network' || type === 'access-denied' ? type : 'unknown');
    this.selected.set(new Set());
    this.store.dispatch(
      ReviewsActions.pageChanged({ pageIndex: this.pageIndex(), pageSize: this.pageSize() }),
    );
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

function reasonOf(result: BatchItemResult): string {
  const code = result.code ?? '';
  return REASONS.includes(code) ? code : 'unknown';
}
