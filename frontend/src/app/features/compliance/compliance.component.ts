import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatChip } from '@angular/material/chips';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIcon } from '@angular/material/icon';
import { MatPaginator, MatPaginatorIntl, PageEvent } from '@angular/material/paginator';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabChangeEvent, MatTabsModule } from '@angular/material/tabs';
import { ActivatedRoute, ParamMap, Params, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Store } from '@ngrx/store';
import { distinctUntilChanged, map } from 'rxjs';

import { LanguageService } from '../../core/i18n/language.service';
import { kyivDateTime } from '../../shared/date-format';
import { TranslatedPaginatorIntl } from '../../shared/translated-paginator-intl';
import { UserRef } from '../reviews/reviews.service';
import { Phrase, durationPhrase, isErasedEmail, periodPhrases } from './compliance-format';
import { ErasureEntry, ErasureStatus, RetentionRun } from './compliance.service';
import { ComplianceActions } from './store/compliance.actions';
import { complianceFeature } from './store/compliance.feature';

export type ComplianceTab = 'retention' | 'erasures' | 'holds';

/** What the URL asks the page to show. */
export interface ComplianceView {
  tab: ComplianceTab;
  status: ErasureStatus | null;
  active: boolean;
  page: number;
  size: number;
}

const TABS: ComplianceTab[] = ['retention', 'erasures', 'holds'];
const STATUSES: ErasureStatus[] = ['PENDING', 'CANCELLED', 'COMPLETED'];
const PAGE_SIZES = [20, 50, 100];
const POLICIES = [
  'notifications',
  'one-time-tokens',
  'authorizations',
  'devices',
  'audit-partitions',
  'pending-accounts',
  'former-staff',
  'consent-records',
  'audit-logs',
];

/**
 * Reads the page state from the query parameters; anything unknown falls back to the default.
 *
 * @param params the query parameters of `/compliance`
 * @returns the tab, its filter and its page
 */
export function readView(params: ParamMap): ComplianceView {
  const tab = params.get('tab') as ComplianceTab;
  const status = params.get('status') as ErasureStatus;
  const page = Number(params.get('page'));
  const size = Number(params.get('size'));
  return {
    tab: TABS.includes(tab) ? tab : 'retention',
    status: STATUSES.includes(status) ? status : null,
    active: params.get('holds') !== 'all',
    page: Number.isInteger(page) && page > 0 ? page : 0,
    size: PAGE_SIZES.includes(size) ? size : PAGE_SIZES[0],
  };
}

@Component({
  selector: 'app-compliance',
  imports: [
    NgTemplateOutlet,
    MatTabsModule,
    MatTableModule,
    MatExpansionModule,
    MatButtonToggleGroup,
    MatButtonToggle,
    MatChip,
    MatIcon,
    MatButton,
    MatPaginator,
    MatProgressBar,
    RouterLink,
    TranslocoPipe,
  ],
  providers: [{ provide: MatPaginatorIntl, useClass: TranslatedPaginatorIntl }],
  templateUrl: './compliance.component.html',
  styleUrl: './compliance.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComplianceComponent {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly language = inject(LanguageService);

  protected readonly statuses = STATUSES;
  protected readonly pageSizes = PAGE_SIZES;
  protected readonly policyColumns = ['policy', 'period', 'action'];
  protected readonly resultColumns = ['policy', 'affected', 'held', 'outcome'];
  protected readonly erasureColumns = [
    'person',
    'origin',
    'status',
    'requestedAt',
    'scheduledFor',
    'closedAt',
  ];
  protected readonly holdColumns = ['person', 'reason', 'placed', 'lifted'];

  readonly view = toSignal(this.route.queryParamMap.pipe(map(readView)), {
    initialValue: readView(this.route.snapshot.queryParamMap),
  });
  readonly tabIndex = computed(() => TABS.indexOf(this.view().tab));

  readonly policies = this.store.selectSignal(complianceFeature.selectPolicies);
  readonly policiesLoading = this.store.selectSignal(complianceFeature.selectPoliciesLoading);
  readonly policiesProblem = this.store.selectSignal(complianceFeature.selectPoliciesProblem);
  readonly runs = this.store.selectSignal(complianceFeature.selectRuns);
  readonly erasures = this.store.selectSignal(complianceFeature.selectErasures);
  readonly holds = this.store.selectSignal(complianceFeature.selectHolds);

  constructor() {
    this.route.queryParamMap
      .pipe(
        map(readView),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        takeUntilDestroyed(),
      )
      .subscribe((view) => this.load(view));
  }

  retry(): void {
    this.load(this.view());
  }

  selectTab(event: MatTabChangeEvent): void {
    const tab = TABS[event.index];
    if (tab !== this.view().tab) {
      this.navigate({ tab, page: null });
    }
  }

  filterStatus(status: ErasureStatus | ''): void {
    this.navigate({ status: status || null, page: null });
  }

  filterHolds(scope: 'active' | 'all'): void {
    this.navigate({ holds: scope === 'all' ? 'all' : null, page: null });
  }

  changePage(event: PageEvent): void {
    this.navigate({
      page: event.pageIndex || null,
      size: event.pageSize === PAGE_SIZES[0] ? null : event.pageSize,
    });
  }

  policyName(key: string): string {
    return POLICIES.includes(key) ? `compliance.policies.${key}` : '';
  }

  period(period: string): Phrase[] {
    return periodPhrases(period);
  }

  duration(run: RetentionRun): Phrase | null {
    return durationPhrase(run.startedAt, run.finishedAt);
  }

  failures(run: RetentionRun): number {
    return run.results.filter((result) => result.failed).length;
  }

  closedAt(request: ErasureEntry): string | null {
    return request.completedAt ?? request.cancelledAt;
  }

  postponed(request: ErasureEntry): boolean {
    return request.status === 'PENDING' && request.postponedAt !== null;
  }

  email(person: UserRef): string | null {
    return isErasedEmail(person.email) ? null : person.email;
  }

  time(value: string | null): string {
    return value ? kyivDateTime(value, this.language.current()) : '—';
  }

  private load(view: ComplianceView): void {
    const request = { page: view.page, size: view.size };
    if (view.tab === 'erasures') {
      this.store.dispatch(ComplianceActions.erasuresOpened({ request, status: view.status }));
    } else if (view.tab === 'holds') {
      this.store.dispatch(ComplianceActions.holdsOpened({ request, active: view.active }));
    } else {
      this.store.dispatch(ComplianceActions.retentionOpened({ request }));
    }
  }

  private navigate(queryParams: Params): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge',
    });
  }
}
