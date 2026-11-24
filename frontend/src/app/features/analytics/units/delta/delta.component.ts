import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  computed,
  input,
} from '@angular/core';
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
    @if (chip()) {
      <span class="delta-chip" data-testid="delta">
        @if (view().fresh) {
          {{ 'unitAnalytics.delta.new' | transloco }}
        } @else {
          {{ view().percent ?? view().count }}
        }
        <span class="cdk-visually-hidden">
          {{ 'unitAnalytics.delta.previous' | transloco: { count: figure().previous } }}
        </span>
      </span>
    } @else {
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
    }
  `,
  styles: `
    .delta-chip {
      display: inline-block;
      padding: 3px 8px;
      border-radius: 999px;
      background: var(--app-pane-head);
      color: var(--app-bar);
      font: 700 0.6875rem / 1.3 var(--app-font-heading);
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }

    .delta {
      display: inline-flex;
      align-items: center;
      gap: 2px;
      font: 600 0.75rem / 1.4 var(--app-font-body);
      color: var(--app-delta-color, var(--mat-sys-primary));
      white-space: nowrap;
    }

    .delta__icon {
      --app-icon-size: 18px;
    }
  `,
})
export class DeltaComponent {
  readonly figure = input.required<Figure>();
  /** True for the neutral chip of a table cell: the signed percentage (or count), "new" without a previous count. */
  readonly chip = input(false, { transform: booleanAttribute });

  protected readonly view = computed(() => deltaView(this.figure()));
  protected readonly icon = computed(() => ICONS[this.view().direction]);
}
