import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../../core/i18n/language.service';
import { categoryName } from '../../../awards/awards.service';
import { DeltaComponent } from '../../units/delta/delta.component';
import { Figure } from '../../units/unit-analytics.service';
import { AnalyticsTrends, CategoryTrend, SourceTrend } from '../university-analytics.service';

/** An entry of a trend list: a category or an awarding organisation with its two counts. */
export interface TrendEntry {
  key: string;
  name: string;
  figure: Figure;
}

/** A rising and a falling list, for the categories or for the awarding organisations. */
export interface TrendGroup {
  id: 'categories' | 'sources';
  title: string;
  empty: string;
  rising: TrendEntry[];
  falling: TrendEntry[];
}

/** The categories and awarding organisations that changed noticeably against the previous period. */
@Component({
  selector: 'app-trend-lists',
  imports: [DeltaComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './trend-lists.component.html',
  styleUrl: './trend-lists.component.scss',
})
export class TrendListsComponent {
  private readonly language = inject(LanguageService);

  readonly trends = input.required<AnalyticsTrends>();

  protected readonly groups = computed<TrendGroup[]>(() => {
    const language = this.language.current();
    const { categories, sources } = this.trends();
    const category = (entry: CategoryTrend): TrendEntry => ({
      key: String(entry.category.id),
      name: categoryName(entry.category, language),
      figure: entry,
    });
    const source = (entry: SourceTrend): TrendEntry => ({
      key: entry.name,
      name: entry.name,
      figure: entry,
    });
    return [
      {
        id: 'categories',
        title: 'universityAnalytics.trends.categories',
        empty: 'universityAnalytics.trends.noCategory',
        rising: categories.rising.map(category),
        falling: categories.falling.map(category),
      },
      {
        id: 'sources',
        title: 'universityAnalytics.trends.sources',
        empty: 'universityAnalytics.trends.noSource',
        rising: sources.rising.map(source),
        falling: sources.falling.map(source),
      },
    ];
  });
}
