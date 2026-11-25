import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { CountUpDirective } from '../../../../shared/effects/count-up.directive';
import { StaggerDirective } from '../../../../shared/effects/stagger.directive';
import { sparkPoints } from '../../../../shared/widgets/kpi-widget.component';
import { periodYears } from '../delta';
import { DeltaComponent } from '../delta/delta.component';
import { UnitTotals } from '../unit-analytics.service';

/**
 * Headline figures of a unit: approved awards and awarded people with their change, unit awards, verified,
 * per 100 members; as bento tiles the changes are neutral chips and the approved tile draws its years.
 */
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
  /** The approved awards per year, oldest first, drawn as a sparkline on the approved tile from two years on. */
  readonly years = input<number[]>([]);
  /** The period the changes compare with, printed under each change; null prints no caption. */
  readonly previousPeriod = input<{ from: number; to: number } | null>(null);

  protected readonly previous = computed(() => {
    const period = this.previousPeriod();
    return period ? periodYears(period) : null;
  });
  protected readonly bento = computed(() => this.layout() === 'bento');
  protected readonly points = computed(() =>
    this.years().length > 1 ? sparkPoints(this.years()) : null,
  );
}
