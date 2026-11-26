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
            <span class="stairs__block" [style.--w]="step.height + '%'" aria-hidden="true"></span>
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
      gap: 0;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .stairs__step {
      display: grid;
      grid-template-columns: minmax(96px, 1.1fr) minmax(48px, 1.4fr) 28px;
      grid-template-areas: 'label bar count';
      align-items: center;
      gap: 10px;
      padding: 6px 0;
      font: 400 0.8125rem / 1.35 var(--app-font-body);
    }

    .stairs__label {
      grid-area: label;
      overflow-wrap: normal;
      word-break: normal;
    }

    .stairs__block {
      grid-area: bar;
      display: block;
      height: 10px;
      border-radius: 5px;
      background:
        linear-gradient(var(--app-bar-gold), var(--app-bar-gold)) no-repeat left / var(--w, 0%) 100%,
        var(--app-track);
    }

    .stairs__count {
      grid-area: count;
      text-align: end;
      font: 700 0.8125rem / 1 var(--app-font-heading);
      font-variant-numeric: tabular-nums;
    }

    .stairs__step--empty .stairs__count {
      color: var(--app-muted);
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
