import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  computed,
  input,
} from '@angular/core';

const PERCENT = 100;
const MIN_BAR_PERCENT = 4;

/** One row of a bar list. */
export interface BarRow {
  key: string | number;
  label: string;
  value: number;
  /** True for the row drawn in the primary colour (the current year); the others are muted. */
  current?: boolean;
}

/** W1: thick horizontal bars, label on the left and the count at the end, scaled to the largest value. */
@Component({
  selector: 'app-bar-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol class="bars" [class.bars--short]="shortLabels()" [attr.aria-label]="label()">
      @for (row of bars(); track row.key) {
        <li class="bars__row" [class.bars__row--current]="row.current" data-testid="bar-row">
          <span class="bars__label">{{ row.label }}</span>
          <span class="bars__track" aria-hidden="true">
            <i class="bars__fill" [style.width.%]="row.width"></i>
          </span>
          <b class="bars__count">{{ row.value }}</b>
        </li>
      }
    </ol>
  `,
  styles: `
    :host {
      display: block;
    }

    .bars {
      display: grid;
      gap: 6px;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .bars__row {
      display: grid;
      grid-template-columns: minmax(0, 1.6fr) minmax(56px, 1fr) 32px;
      align-items: center;
      gap: 10px;
      font: 400 0.8125rem / 1.35 var(--app-font-body);
    }

    .bars--short .bars__row {
      grid-template-columns: 44px minmax(0, 1fr) 32px;
    }

    .bars--short .bars__label {
      font: 800 0.8125rem / 1 var(--app-font-heading);
      color: var(--app-muted);
      font-variant-numeric: tabular-nums;
    }

    .bars--short .bars__row--current .bars__label {
      color: var(--app-bar);
    }

    .bars__track {
      display: block;
      height: 18px;
      overflow: hidden;
      border-radius: var(--app-radius-sm);
      background: var(--app-track);
    }

    .bars__fill {
      display: block;
      height: 100%;
      border-radius: var(--app-radius-sm);
      background: color-mix(in srgb, var(--app-bar) 45%, var(--app-track));
    }

    .bars__row--current .bars__fill {
      background: var(--app-bar);
    }

    .bars__count {
      text-align: end;
      font: 800 0.875rem / 1 var(--app-font-heading);
      font-variant-numeric: tabular-nums;
    }
  `,
})
export class BarListComponent {
  /** The rows in the order to draw them. */
  readonly rows = input.required<BarRow[]>();
  /** The accessible name of the list. */
  readonly label = input.required<string>();
  /** True when the labels are short (years), so the label column stays narrow and bold. */
  readonly shortLabels = input(false, { transform: booleanAttribute });

  /** The value of a full bar when it is not the largest row (a list continuing the cards above it). */
  readonly max = input<number | null>(null);

  protected readonly bars = computed(() => {
    const max = this.max() ?? Math.max(0, ...this.rows().map((row) => row.value));
    return this.rows().map((row) => ({
      ...row,
      width: row.value > 0 && max > 0 ? Math.max(MIN_BAR_PERCENT, (row.value * PERCENT) / max) : 0,
    }));
  });
}
