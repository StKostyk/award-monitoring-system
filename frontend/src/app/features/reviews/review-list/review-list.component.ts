import { BreakpointObserver } from '@angular/cdk/layout';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatPaginator, MatPaginatorIntl, PageEvent } from '@angular/material/paginator';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatOption, MatSelect } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Store } from '@ngrx/store';
import { filter as present, forkJoin, map, tap } from 'rxjs';

import { knownProblem } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { approvalScopes, reviewableLevels } from '../../../core/auth/permissions';
import { LanguageService } from '../../../core/i18n/language.service';
import { WIDE_LAYOUT } from '../../../core/layout/shell.component';
import {
  OrganizationSummary,
  OrganizationsService,
} from '../../../core/organizations/organizations.service';
import { KyivDatePipe } from '../../../shared/kyiv-date.pipe';
import { ListRowComponent, ListRowsDirective } from '../../../shared/layout/list-row.component';
import { PageHeaderComponent } from '../../../shared/layout/page-header.component';
import { watchLeftSelection } from '../../../shared/layout/left-selection';
import { PaneComponent } from '../../../shared/layout/pane.component';
import { querySelection } from '../../../shared/layout/query-selection';
import { SplitComponent } from '../../../shared/layout/split.component';
import { organizationName } from '../../../shared/organization-name';
import { TranslatedPaginatorIntl } from '../../../shared/translated-paginator-intl';
import { initials } from '../../../shared/widgets/top-widget.component';
import { ApprovalLevel, awardTitle } from '../../awards/awards.service';
import {
  DecisionDialogComponent,
  DecisionDialogData,
  DecisionInput,
} from '../decision-dialog/decision-dialog.component';
import { HeldDecisionService } from '../held-decision.service';
import { ReviewPeriodComponent } from '../review-period/review-period.component';
import { ReviewQueueComponent } from '../review-queue/review-queue.component';
import { ReworkListComponent } from '../rework-list/rework-list.component';
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

/** The `tab` query parameter of the in-rework tab. */
const REWORK = 'rework';

/** Failure reasons the batch summary names; any other code reads as `unknown`. */
const REASONS = [
  'request-claimed',
  'request-stale',
  'request-closed',
  'no-higher-level',
  'escalation-not-upward',
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
    MatFormField,
    MatLabel,
    MatSelect,
    MatOption,
    MatProgressBar,
    MatPaginator,
    ListRowComponent,
    ListRowsDirective,
    PageHeaderComponent,
    PaneComponent,
    SplitComponent,
    TranslocoPipe,
    KyivDatePipe,
    ReviewPeriodComponent,
    ReviewQueueComponent,
    ReworkListComponent,
  ],
  providers: [{ provide: MatPaginatorIntl, useClass: TranslatedPaginatorIntl }],
  templateUrl: './review-list.component.html',
  styleUrl: './review-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReviewListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly shownRequest = querySelection('request');
  private readonly auth = inject(AuthService);
  private readonly organizationList = inject(OrganizationsService);
  private readonly language = inject(LanguageService);
  private readonly dialog = inject(MatDialog);
  private readonly reviews = inject(ReviewsService);
  private readonly heldDecisions = inject(HeldDecisionService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly tabs = TABS;
  protected readonly levels = reviewableLevels(this.auth.permissions()) as ApprovalLevel[];
  protected readonly lowerLevel: ApprovalLevel | null =
    this.levels.at(-1) === 'DEAN' ? 'FACULTY_SECRETARY' : null;
  protected readonly pageSizes = PAGE_SIZES;
  protected readonly units = signal<OrganizationSummary[]>([]);
  private readonly wide = toSignal(
    inject(BreakpointObserver)
      .observe(WIDE_LAYOUT)
      .pipe(map((state) => state.matches)),
    { initialValue: true },
  );
  /** The award chosen with `?request=`, null when none is. */
  readonly selectedId = this.shownRequest.id;

  readonly items = this.store.selectSignal(reviewsFeature.selectItems);
  /** The requests of the page without those whose decision is held. */
  readonly visible = computed(() =>
    this.items().filter((item) => !this.heldDecisions.heldIds().has(item.awardId)),
  );
  /**
   * The request in the pane: the one in the URL, else the first of the page beside the list at 960 px and wider;
   * below that the pane shows only a request the reviewer opened, so the shortcuts never act on a hidden one.
   */
  readonly shownId = computed(
    () => this.selectedId() ?? (this.wide() ? (this.visible()[0]?.awardId ?? null) : null),
  );
  readonly filters = this.store.selectSignal(reviewsFeature.selectFilters);
  readonly loading = this.store.selectSignal(reviewsFeature.selectLoading);
  readonly problem = this.store.selectSignal(reviewsFeature.selectProblem);
  readonly total = this.store.selectSignal(reviewsFeature.selectTotal);
  readonly pageIndex = this.store.selectSignal(reviewsFeature.selectPageIndex);
  readonly pageSize = this.store.selectSignal(reviewsFeature.selectPageSize);
  /** True on the in-rework tab, kept in the URL as `?tab=rework`. */
  readonly rework = toSignal(
    this.route.queryParamMap.pipe(map((params) => params.get('tab') === REWORK)),
    { initialValue: this.route.snapshot.queryParamMap.get('tab') === REWORK },
  );
  /** How many awards wait in rework; null until read or when the read failed. */
  readonly reworkCount = signal<number | null>(null);
  /** A message handed over by the page the reviewer came from. */
  readonly notice = signal<string | null>(null);

  /** Award ids the reviewer ticked; only those on the current page count. */
  readonly ticked = signal<ReadonlySet<number>>(new Set());
  readonly selection = computed(() =>
    this.visible().filter((item) => this.ticked().has(item.awardId)),
  );
  readonly allTicked = computed(
    () => this.visible().length > 0 && this.selection().length === this.visible().length,
  );
  /** «Передати декану» when every ticked request waits at the faculty secretary. */
  readonly escalateTarget = computed<ApprovalLevel | null>(() =>
    this.selection().every((item) => item.level === 'FACULTY_SECRETARY') ? 'DEAN' : null,
  );
  readonly busy = signal(false);
  readonly summary = signal<BatchSummary | null>(null);

  constructor() {
    this.heldDecisions.returned.pipe(takeUntilDestroyed()).subscribe(() => this.refresh());
    watchLeftSelection(
      {
        selectedId: this.selectedId,
        ids: computed(() => this.items().map((item) => item.awardId)),
        settled: computed(() => !this.loading() && !this.problem()),
        check: (awardId) => this.reviews.item(awardId),
      },
      () => {
        this.select(null);
        this.snackBar.open(this.transloco.translate('reviews.queue.selectionGone'), undefined, {
          duration: 5000,
        });
      },
    );
  }

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
    this.reviews.returned(0, 1).subscribe({
      next: (page) => this.reworkCount.set(page.totalElements),
      error: () => this.reworkCount.set(null),
    });
  }

  /**
   * Shows the queue with an assignment filter, leaving the in-rework tab.
   *
   * @param assigned whose requests to show
   */
  showQueue(assigned: ReviewAssignment | null): void {
    if (this.rework()) {
      this.openTab(null);
    }
    this.filter({ assigned });
  }

  /** Shows the awards sent back for rework instead of the queue. */
  showRework(): void {
    if (!this.rework()) {
      this.openTab(REWORK);
    }
  }

  filter(change: Partial<ReviewFilters>): void {
    this.ticked.set(new Set());
    this.store.dispatch(
      ReviewsActions.filtersChanged({ filters: { ...this.filters(), ...change } }),
    );
  }

  page(event: PageEvent): void {
    this.ticked.set(new Set());
    this.store.dispatch(
      ReviewsActions.pageChanged({ pageIndex: event.pageIndex, pageSize: event.pageSize }),
    );
  }

  /**
   * Shows a request in the pane and keeps it in the URL; null goes back to the queue.
   *
   * @param awardId the award of the request
   */
  select(awardId: number | null): void {
    this.shownRequest.select(awardId);
  }

  isShown(item: ReviewItem): boolean {
    return item.awardId === this.shownId();
  }

  initialsOf(item: ReviewItem): string {
    return initials(this.recipient(item));
  }

  isTicked(item: ReviewItem): boolean {
    return this.ticked().has(item.awardId);
  }

  toggle(item: ReviewItem): void {
    const next = new Set(this.ticked());
    if (!next.delete(item.awardId)) {
      next.add(item.awardId);
    }
    this.ticked.set(next);
  }

  toggleAll(): void {
    this.ticked.set(
      this.allTicked() ? new Set() : new Set(this.visible().map((item) => item.awardId)),
    );
  }

  /** Confirms a decision for the ticked awards and applies it to each of them. */
  decide(decision: DecisionType): void {
    const chosen = this.selection();
    if (chosen.length === 0 || this.busy()) {
      return;
    }
    const data: DecisionDialogData<BatchItemResult[]> = {
      decision,
      target: decision === 'ESCALATE' ? this.escalateTarget() : null,
      documents: 0,
      count: chosen.length,
      draft: `batch:${chosen.map((item) => item.awardId).join('-')}`,
      submit: (input: DecisionInput) => {
        this.busy.set(true);
        this.summary.set(null);
        return this.reviews
          .decideBatch({
            decision,
            ...input,
            items: chosen.map((item) => ({
              awardId: item.awardId,
              requestVersion: item.requestVersion,
            })),
          })
          .pipe(tap({ error: () => this.busy.set(false) }));
      },
    };
    this.dialog
      .open<DecisionDialogComponent, DecisionDialogData<BatchItemResult[]>, BatchItemResult[]>(
        DecisionDialogComponent,
        { data, width: '480px' },
      )
      .afterClosed()
      .pipe(present((results): results is BatchItemResult[] => !!results))
      .subscribe((results) => this.decided(chosen, results));
  }

  clear(): void {
    this.ticked.set(new Set());
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

  unitName(unit: OrganizationSummary): string {
    return organizationName(unit, this.language.current());
  }

  /**
   * Takes a request whose decision the queue holds out of the list.
   *
   * @param awardId the award decided
   */
  queueDecided(awardId: number): void {
    this.ticked.update((ticked) => new Set([...ticked].filter((id) => id !== awardId)));
    this.store.dispatch(ReviewsActions.itemsDecided({ awardIds: [awardId] }));
  }

  /** Reads the page of the queue again. */
  refresh(): void {
    this.store.dispatch(
      ReviewsActions.pageChanged({ pageIndex: this.pageIndex(), pageSize: this.pageSize() }),
    );
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
    this.ticked.set(new Set(failed.map((result) => result.awardId)));
    const done = results
      .filter((result) => result.outcome === 'DONE')
      .map((result) => result.awardId);
    const selected = this.selectedId();
    if (selected !== null && done.includes(selected)) {
      this.select(null);
    }
    this.store.dispatch(ReviewsActions.itemsDecided({ awardIds: done }));
    if (failed.length > 0) {
      this.refresh();
    }
  }

  private openTab(tab: string | null): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab, request: null, returned: null },
      queryParamsHandling: 'merge',
    });
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
  return knownProblem(result.code ?? '', REASONS);
}
