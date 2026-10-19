import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { KyivDatePipe } from '../../../shared/kyiv-date.pipe';
import { PersonalAnalytics } from '../analytics.service';

/** Headline figures of the period: approved, verified, highest level, first and latest award. */
@Component({
  selector: 'app-summary-tiles',
  imports: [KyivDatePipe, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './summary-tiles.component.html',
  styleUrl: './summary-tiles.component.scss',
})
export class SummaryTilesComponent {
  readonly totals = input.required<PersonalAnalytics['totals']>();
}
