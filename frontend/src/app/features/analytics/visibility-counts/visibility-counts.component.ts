import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { AwardVisibility } from '../../awards/awards.service';
import { AwardVisibilityCounts } from '../analytics.service';

/** One row of the widget: a visibility, its count and the filter of the award list it opens. */
interface VisibilityRow {
  visibility: AwardVisibility;
  count: number;
}

/** Approved personal awards by whom they are shown to, each count opening the award list filtered by it. */
@Component({
  selector: 'app-visibility-counts',
  imports: [RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="visibility__list">
      @for (row of rows(); track row.visibility) {
        <li class="visibility__row">
          <a
            class="visibility__link"
            routerLink="/awards"
            [queryParams]="{ status: 'APPROVED', visibility: row.visibility }"
            [attr.data-testid]="'visibility-' + row.visibility"
          >
            <span class="visibility__count">{{ row.count }}</span>
            <span>{{ 'analytics.visibility.' + row.visibility | transloco }}</span>
          </a>
        </li>
      }
    </ul>
    <p class="visibility__documents" data-testid="visibility-documents">
      {{ 'analytics.visibility.withDocument' | transloco: { count: counts().withDocument } }}
    </p>
  `,
  styles: `
    :host {
      display: block;
    }
    .visibility__list {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .visibility__row {
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    .visibility__link {
      display: flex;
      align-items: baseline;
      gap: 12px;
      min-height: 48px;
      align-content: center;
      flex-wrap: wrap;
    }
    .visibility__count {
      min-width: 2ch;
      font: var(--mat-sys-title-large);
      font-variant-numeric: tabular-nums;
    }
    .visibility__documents {
      margin: 12px 0 0;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class VisibilityCountsComponent {
  readonly counts = input.required<AwardVisibilityCounts>();

  protected readonly rows = computed<VisibilityRow[]>(() => {
    const counts = this.counts();
    return [
      { visibility: 'PUBLIC', count: counts.publicAwards },
      { visibility: 'UNIVERSITY', count: counts.universityAwards },
      { visibility: 'PRIVATE', count: counts.privateAwards },
    ];
  });
}
