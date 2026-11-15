import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { IconComponent } from '../../../../shared/icons/icon.component';
import { deltaView } from '../delta';
import { Figure } from '../unit-analytics.service';

const ICONS = { up: 'arrow-up', down: 'arrow-down', same: 'arrow-right' } as const;

/** The change of a figure against the previous period: arrow, signed count and percentage, or "new". */
@Component({
  selector: 'app-delta',
  imports: [IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span [class]="'delta delta--' + view().direction" data-testid="delta">
      <app-icon class="delta__icon" [name]="icon()" />
      @if (view().fresh) {
        <span>{{ view().count }} · {{ 'unitAnalytics.delta.new' | transloco }}</span>
      } @else if (view().percent) {
        <span>{{ view().count }} ({{ view().percent }})</span>
      } @else {
        <span>{{ view().count }}</span>
      }
      <span class="cdk-visually-hidden">
        {{ 'unitAnalytics.delta.previous' | transloco: { count: figure().previous } }}
      </span>
    </span>
  `,
  styles: `
    .delta {
      display: inline-flex;
      align-items: center;
      gap: 2px;
      font: var(--mat-sys-label-large);
      color: var(--mat-sys-on-surface-variant);
      white-space: nowrap;
    }

    .delta__icon {
      --app-icon-size: 18px;
    }
  `,
})
export class DeltaComponent {
  readonly figure = input.required<Figure>();

  protected readonly view = computed(() => deltaView(this.figure()));
  protected readonly icon = computed(() => ICONS[this.view().direction]);
}
