import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { EmptyStateComponent } from '../empty-state/empty-state.component';
import { LabelledCount } from './widget-data';

const PERCENT = 100;
const MIN_STEP_PERCENT = 20;

/** V3: the levels as steps from the unit levels up to the international one, each with its count. */
@Component({
  selector: 'app-level-stairs-widget',
  imports: [EmptyStateComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (total() > 0) {
      <ol
        class="stairs"
        data-testid="level-stairs"
        [attr.aria-label]="'widgets.stairs.list' | transloco"
      >
        @for (step of rows(); track step.key) {
          <li class="stairs__step" [class.stairs__step--empty]="step.count === 0">
            <span class="stairs__block" [style.height.%]="step.height" aria-hidden="true"></span>
            <span class="stairs__count">{{ step.count }}</span>
            <span class="stairs__label">{{ step.key | transloco }}</span>
          </li>
        }
      </ol>
    } @else {
      <app-empty-state icon="chart-column" [text]="'widgets.empty' | transloco" />
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .stairs {
      display: grid;
      grid-template-columns: repeat(5, minmax(0, 1fr));
      align-items: end;
      gap: 4px;
      height: 200px;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .stairs__step {
      display: grid;
      grid-template-rows: 1fr auto auto;
      align-items: end;
      height: 100%;
      text-align: center;
    }

    .stairs__block {
      display: block;
      align-self: end;
      border-radius: 4px 4px 0 0;
      background: var(--app-chart-bar);
    }

    .stairs__step--empty .stairs__block {
      background: var(--mat-sys-surface-container-high);
    }

    .stairs__count {
      font: var(--mat-sys-title-medium);
      font-variant-numeric: tabular-nums;
    }

    .stairs__label {
      overflow-wrap: anywhere;
      font: var(--mat-sys-label-small);
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class LevelStairsWidgetComponent {
  /** The steps from the lowest level to the highest, from `levelSteps()` in `facts.ts`. */
  readonly steps = input.required<LabelledCount[]>();

  protected readonly total = computed(() =>
    this.steps().reduce((sum, step) => sum + step.count, 0),
  );
  protected readonly rows = computed(() => {
    const count = this.steps().length;
    return this.steps().map((step, index) => ({
      ...step,
      height: MIN_STEP_PERCENT + ((PERCENT - MIN_STEP_PERCENT) * (index + 1)) / count,
    }));
  });
}
