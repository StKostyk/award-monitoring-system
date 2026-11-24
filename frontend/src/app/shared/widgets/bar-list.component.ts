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
  /** The translated mark of a period that is not over yet; the rest of its track is hatched. */
  running?: string;
}

/** W1: thick horizontal bars, label on the left and the count at the end, scaled to the largest value. */
@Component({
  selector: 'app-bar-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol
      class="bars"
      [class.bars--short]="shortLabels()"
      [class.bars--thick]="thick()"
      [attr.aria-label]="label()"
    >
      @for (row of bars(); track row.key) {
        <li class="bars__row" [class.bars__row--current]="row.current" data-testid="bar-row">
          <span class="bars__label">{{ row.label }}</span>
          <span class="bars__track" [class.bars__track--running]="row.running">
            <i class="bars__fill" [style.width.%]="row.width" aria-hidden="true"></i>
            @if (row.running) {
              <span class="bars__running" data-testid="bar-running">{{ row.running }}</span>
            }
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

    .bars--thick {
      gap: 8px;
    }

    .bars--thick .bars__track {
      height: 26px;
      border-radius: var(--app-radius-sm);
    }

    .bars--thick .bars__fill {
      border-radius: var(--app-radius-sm);
    }

    .bars--thick .bars__count {
      font-size: 0.9375rem;
    }

    .bars__track--running {
      position: relative;
    }

    .bars__track--running .bars__fill {
      background:
        repeating-linear-gradient(
          135deg,
          transparent 0 6px,
          color-mix(in srgb, var(--app-pane) 30%, transparent) 6px 10px
        ),
        var(--app-bar);
    }

    .bars__running {
      position: absolute;
      inset-inline-end: 6px;
      top: 50%;
      transform: translateY(-50%);
      padding: 1px 6px;
      border-radius: 999px;
      background: var(--app-pane);
      color: var(--app-bar);
      font: 700 0.6875rem / 1.3 var(--app-font-heading);
      white-space: nowrap;
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
  /** True for 26 px bars, the size of a widget that shows nothing else. */
  readonly thick = input(false, { transform: booleanAttribute });

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
