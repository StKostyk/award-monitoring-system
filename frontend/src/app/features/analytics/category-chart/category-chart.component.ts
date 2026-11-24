import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
} from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { LanguageService } from '../../../core/i18n/language.service';
import { PaneComponent } from '../../../shared/layout/pane.component';
import { BarListComponent, BarRow } from '../../../shared/widgets/bar-list.component';
import { CategoryRef, categoryName } from '../../awards/awards.service';

const TOP_CARDS = 3;
const PERCENT = 100;

/** One category with its place, count and share of all approved awards in the period. */
export interface CategoryRow {
  id: number;
  rank: number;
  name: string;
  level: CategoryRef['level'];
  count: number;
  share: number;
}

/**
 * The categories ranked by count (then by name), each with its share of the total in whole per cent.
 *
 * @param byCategory the counts per category
 * @param language the language of the names
 * @return the ranked rows
 */
export function categoryRows(
  byCategory: { category: CategoryRef; count: number }[],
  language: string,
): CategoryRow[] {
  const total = byCategory.reduce((sum, entry) => sum + entry.count, 0);
  return byCategory
    .map((entry) => ({
      id: entry.category.id,
      name: categoryName(entry.category, language),
      level: entry.category.level,
      count: entry.count,
    }))
    .sort((a, b) => b.count - a.count || a.name.localeCompare(b.name, language))
    .map((row, index) => ({
      ...row,
      rank: index + 1,
      share: total > 0 ? Math.round((row.count * PERCENT) / total) : 0,
    }));
}

/** W1 / D: the three most frequent categories as cards, the rest as a ranked list of bars. */
@Component({
  selector: 'app-category-chart',
  imports: [BarListComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './category-chart.component.html',
  styleUrl: './category-chart.component.scss',
})
export class CategoryChartComponent {
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly pane = inject(PaneComponent, { optional: true });

  readonly byCategory = input.required<{ category: CategoryRef; count: number }[]>();

  protected readonly rows = computed(() =>
    categoryRows(this.byCategory(), this.language.current()),
  );
  protected readonly table = computed(() => this.pane?.view() === 'table');
  protected readonly cards = computed(() => this.rows().slice(0, TOP_CARDS));
  protected readonly rest = computed<BarRow[]>(() =>
    this.rows()
      .slice(TOP_CARDS)
      .map((row) => ({ key: row.id, label: row.name, value: row.count })),
  );
  protected readonly restLabel = computed(() => {
    this.language.current();
    return this.transloco.translate('analytics.categories.list');
  });

  constructor() {
    const release = this.pane?.holdChart();
    if (release) {
      inject(DestroyRef).onDestroy(release);
    }
  }
}
