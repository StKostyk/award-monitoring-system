import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { MatSort, MatSortHeader, Sort } from '@angular/material/sort';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { LanguageService } from '../../../../core/i18n/language.service';
import { PaneComponent } from '../../../../shared/layout/pane.component';
import { organizationName } from '../../../../shared/organization-name';
import { TableScrollComponent } from '../../../../shared/widgets/table-scroll.component';
import { AnalyticsPeriod } from '../../analytics.service';
import { DeltaComponent } from '../delta/delta.component';
import { UnitComparisonRow, UnitTotals } from '../unit-analytics.service';

export type ComparisonColumn =
  'name' | 'members' | 'approved' | 'change' | 'awardedPeople' | 'perHundred' | 'verified';

interface NamedRow {
  row: UnitComparisonRow;
  name: string;
  link: boolean;
  /** Width of the approved bar in per cent of the largest approved count. */
  approvedWidth: number;
  /** Width of the per-100 bar in per cent of the largest value, 0 without members. */
  perHundredWidth: number;
}

/** The translation keys the comparison uses for its caption and the summary row. */
export interface ComparisonLabels {
  caption: string;
  levelRow: string;
  total: string;
}

const DEPARTMENT_LABELS: ComparisonLabels = {
  caption: 'unitAnalytics.sections.comparison',
  levelRow: 'unitAnalytics.comparison.facultyLevel',
  total: 'unitAnalytics.comparison.facultyTotal',
};

const PERCENT = 100;
const MIN_BAR_PERCENT = 4;

/** W2 B: the child units of a faculty or the university ranked with inline bars; the table view shows every column. */
@Component({
  selector: 'app-unit-comparison',
  imports: [
    DecimalPipe,
    DeltaComponent,
    MatSort,
    MatSortHeader,
    NgTemplateOutlet,
    RouterLink,
    TableScrollComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './unit-comparison.component.html',
  styleUrl: './unit-comparison.component.scss',
})
export class UnitComparisonComponent {
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly pane = inject(PaneComponent, { optional: true });

  readonly rows = input.required<UnitComparisonRow[]>();
  readonly period = input.required<AnalyticsPeriod>();
  /** The totals of the unit compared, the last row of the table view; none without. */
  readonly totals = input<UnitTotals | null>(null);
  readonly labels = input<ComparisonLabels>(DEPARTMENT_LABELS);
  readonly linkable = input<(row: UnitComparisonRow) => boolean>(isChildUnit);

  protected readonly table = computed(() => this.pane?.view() === 'table');
  protected readonly sort = signal<Sort>({ active: '', direction: '' });
  protected readonly named = computed<NamedRow[]>(() => {
    const language = this.language.current();
    const levelRow = this.labels().levelRow;
    const linkable = this.linkable();
    const units = this.rows().filter((row) => !row.facultyLevel);
    const maxApproved = Math.max(0, ...units.map((row) => row.approved.current));
    const maxPerHundred = Math.max(0, ...units.map((row) => row.perHundredMembers ?? 0));
    return this.rows().map((row) => ({
      row,
      name: row.facultyLevel
        ? this.transloco.translate(levelRow)
        : organizationName(row.unit, language),
      link: linkable(row),
      approvedWidth: row.facultyLevel ? 0 : width(row.approved.current, maxApproved),
      perHundredWidth: row.facultyLevel ? 0 : width(row.perHundredMembers ?? 0, maxPerHundred),
    }));
  });
  protected readonly sorted = computed(() => {
    const { active, direction } = this.sort();
    if (!active || !direction) {
      return this.named();
    }
    const sign = direction === 'asc' ? 1 : -1;
    const column = active as ComparisonColumn;
    return [...this.named()].sort((a, b) => sign * compare(a, b, column));
  });

  constructor() {
    const release = this.pane?.holdChart();
    if (release) {
      inject(DestroyRef).onDestroy(release);
    }
  }
}

function isChildUnit(row: UnitComparisonRow): boolean {
  return !row.facultyLevel;
}

function compare(a: NamedRow, b: NamedRow, column: ComparisonColumn): number {
  switch (column) {
    case 'name':
      return a.name.localeCompare(b.name);
    case 'members':
      return a.row.members - b.row.members;
    case 'approved':
      return a.row.approved.current - b.row.approved.current;
    case 'change':
      return a.row.approved.delta - b.row.approved.delta;
    case 'awardedPeople':
      return a.row.awardedPeople - b.row.awardedPeople;
    case 'perHundred':
      return (a.row.perHundredMembers ?? -1) - (b.row.perHundredMembers ?? -1);
    case 'verified':
      return a.row.verified - b.row.verified;
  }
}

function width(value: number, max: number): number {
  return value > 0 && max > 0 ? Math.max(MIN_BAR_PERCENT, (value * PERCENT) / max) : 0;
}
