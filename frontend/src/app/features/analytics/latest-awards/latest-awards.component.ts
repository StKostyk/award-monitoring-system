import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../core/i18n/language.service';
import { kyivDate } from '../../../shared/date-format';
import { EmptyStateComponent } from '../../../shared/empty-state/empty-state.component';
import { ListRowComponent, ListRowsDirective } from '../../../shared/layout/list-row.component';
import { awardTitle } from '../../awards/awards.service';
import { TimelineEntry } from '../analytics.service';

/** Awards the latest-awards list shows at most. */
export const LATEST_AWARDS = 5;

/** The latest approved awards of the account, newest first, each linked to its page, and a link to all of them. */
@Component({
  selector: 'app-latest-awards',
  imports: [EmptyStateComponent, ListRowComponent, ListRowsDirective, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (rows().length > 0) {
      <div class="latest__rows" appListRows>
        @for (row of rows(); track row.id) {
          <a
            appListRow
            [routerLink]="['/awards', row.id]"
            [rowTitle]="row.title"
            [meta]="row.meta"
            data-testid="latest-award"
          >
            <span rowEnd [class]="'app-lv app-lv--' + row.level" data-testid="latest-level">{{
              'categories.levels.' + row.level | transloco
            }}</span>
          </a>
        }
      </div>
      <a
        class="latest__all"
        routerLink="/awards"
        [queryParams]="{ status: 'APPROVED' }"
        data-testid="latest-all"
      >
        {{ 'analytics.latest.all' | transloco: { count: total() } }}
      </a>
    } @else {
      <app-empty-state
        icon="award"
        [text]="'analytics.latest.empty' | transloco"
        data-testid="latest-empty"
      />
    }
  `,
  styles: `
    :host {
      display: grid;
    }

    .latest__rows {
      display: grid;
      border-bottom: 1px solid var(--app-row-divider);
    }

    .latest__all {
      justify-self: start;
      padding: 12px 18px;
      color: var(--mat-sys-primary);
      font: 700 0.8125rem / 1.4 var(--app-font-heading);
    }
  `,
})
export class LatestAwardsComponent {
  private readonly language = inject(LanguageService);

  /** The approved awards of the period, newest first. */
  readonly timeline = input.required<TimelineEntry[]>();
  /** How many approved awards the account has in the period. */
  readonly total = input.required<number>();

  protected readonly rows = computed(() => {
    const language = this.language.current();
    return this.timeline()
      .slice(0, LATEST_AWARDS)
      .map((entry) => ({
        id: entry.awardId,
        title: awardTitle(entry, language),
        meta: `${entry.awardingOrganization} · ${kyivDate(entry.awardDate, language)}`,
        level: entry.category.level,
      }));
  });
}
