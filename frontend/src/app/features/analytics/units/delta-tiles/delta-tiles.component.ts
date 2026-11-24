import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { CountUpDirective } from '../../../../shared/effects/count-up.directive';
import { StaggerDirective } from '../../../../shared/effects/stagger.directive';
import { DeltaComponent } from '../delta/delta.component';
import { UnitTotals } from '../unit-analytics.service';

/** Headline figures of a unit: approved awards and awarded people with their change, unit awards, verified, per 100 members. */
@Component({
  selector: 'app-delta-tiles',
  imports: [CountUpDirective, DecimalPipe, DeltaComponent, StaggerDirective, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './delta-tiles.component.html',
  styleUrl: '../../summary-tiles/summary-tiles.component.scss',
  host: { '[class.tiles-bento]': "layout() === 'bento'" },
})
export class DeltaTilesComponent {
  readonly totals = input.required<UnitTotals>();
  /** A 2-column grid inside a pane, or separate bento tiles with the first one in the brand colour. */
  readonly layout = input<'grid' | 'bento'>('grid');
}
