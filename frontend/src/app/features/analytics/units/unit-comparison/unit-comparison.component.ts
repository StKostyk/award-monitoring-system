import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { MatSort, MatSortHeader, Sort } from '@angular/material/sort';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { EChartsCoreOption } from 'echarts/core';

import { LanguageService } from '../../../../core/i18n/language.service';
import { ChartComponent } from '../../../../shared/charts/chart.component';
import { barChartHeight, horizontalBarOption } from '../../../../shared/charts/chart-style';
import { ChartThemeService } from '../../../../shared/charts/chart-theme.service';
import { organizationName } from '../../../../shared/organization-name';
import { AnalyticsPeriod } from '../../analytics.service';
import { DeltaComponent } from '../delta/delta.component';
import { UnitComparisonRow } from '../unit-analytics.service';

export type ComparisonColumn =
  'name' | 'members' | 'approved' | 'change' | 'awardedPeople' | 'perHundred' | 'verified';

interface NamedRow {
  row: UnitComparisonRow;
  name: string;
  link: boolean;
}

/** The translation keys the comparison uses for its caption, the summary row and the chart description. */
export interface ComparisonLabels {
  caption: string;
  levelRow: string;
  chartDescription: string;
}

const DEPARTMENT_LABELS: ComparisonLabels = {
  caption: 'unitAnalytics.sections.comparison',
  levelRow: 'unitAnalytics.comparison.facultyLevel',
  chartDescription: 'unitAnalytics.comparison.chartDescription',
};

const LABEL_WIDTH = 160;

/** The child units of a faculty or the university side by side: a table sortable by every column and approved awards per 100 members as bars. */
@Component({
  selector: 'app-unit-comparison',
  imports: [
    ChartComponent,
    DecimalPipe,
    DeltaComponent,
    MatSort,
    MatSortHeader,
    RouterLink,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './unit-comparison.component.html',
  styleUrl: './unit-comparison.component.scss',
})
export class UnitComparisonComponent {
  private readonly theme = inject(ChartThemeService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  readonly rows = input.required<UnitComparisonRow[]>();
  readonly period = input.required<AnalyticsPeriod>();
  readonly labels = input<ComparisonLabels>(DEPARTMENT_LABELS);
  readonly linkable = input<(row: UnitComparisonRow) => boolean>(isChildUnit);

  protected readonly sort = signal<Sort>({ active: '', direction: '' });
  protected readonly named = computed<NamedRow[]>(() => {
    const language = this.language.current();
    const levelRow = this.labels().levelRow;
    const linkable = this.linkable();
    return this.rows().map((row) => ({
      row,
      name: row.facultyLevel
        ? this.transloco.translate(levelRow)
        : organizationName(row.unit, language),
      link: linkable(row),
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
  protected readonly bars = computed(() =>
    this.named()
      .filter(({ row }) => !row.facultyLevel && row.perHundredMembers !== null)
      .sort((a, b) => (b.row.perHundredMembers ?? 0) - (a.row.perHundredMembers ?? 0))
      .map(({ row, name }) => ({ label: name, value: round(row.perHundredMembers ?? 0) })),
  );
  protected readonly height = computed(() => barChartHeight(this.bars().length));
  protected readonly description = computed(() => {
    this.language.current();
    const top = this.bars()[0];
    return this.transloco.translate(this.labels().chartDescription, {
      count: this.bars().length,
      top: top?.label ?? '',
      value: top?.value ?? 0,
    });
  });
  protected readonly option = computed<EChartsCoreOption>(() =>
    horizontalBarOption(
      this.theme.palette(),
      this.bars(),
      this.transloco.translate('unitAnalytics.comparison.perHundred'),
      LABEL_WIDTH,
    ),
  );
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

function round(value: number): number {
  return Math.round(value * 10) / 10;
}
