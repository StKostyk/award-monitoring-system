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
})
export class DeltaTilesComponent {
  readonly totals = input.required<UnitTotals>();
}
