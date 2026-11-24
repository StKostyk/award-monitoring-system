import { _IdGenerator } from '@angular/cdk/a11y';
import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { IconComponent } from '../icons/icon.component';
import { IconName } from '../icons/icons';

/** The look of a pane: the plain card, the dark ink panel (facts) or the burgundy brand panel (highlights). */
export type PaneTone = 'plain' | 'ink' | 'brand';

/** The form a widget body takes: its chart (or cards) or the table of the same data. */
export type PaneView = 'chart' | 'table';

/**
 * A surface of a split or bento page: a card with an optional heading strip (an icon, the title, a meta slot
 * projected with the paneMeta attribute) and the projected content. A flush pane leaves the padding to its content
 * (lists, KPI grids, banners). A pane whose widget has a table form shows a «Chart / Table» control in its heading
 * and holds the chosen view, which the charts inside read.
 */
@Component({
  selector: 'app-pane',
  imports: [IconComponent, TranslocoPipe],
  template: `
    <section
      class="pane"
      [class.pane--ink]="tone() === 'ink'"
      [class.pane--brand]="tone() === 'brand'"
      [attr.aria-labelledby]="heading() ? headingId : null"
    >
      @if (heading()) {
        <div class="pane__head">
          <h2 class="pane__heading" [id]="headingId">
            @if (icon(); as name) {
              <app-icon class="pane__icon" [name]="name" />
            }
            <span>{{ heading() }}</span>
          </h2>
          <div class="pane__meta">
            <ng-content select="[paneMeta]" />
            @if (switchable()) {
              <span
                class="pane__views"
                role="group"
                [attr.aria-label]="'charts.view' | transloco"
                data-testid="pane-views"
              >
                <button
                  type="button"
                  [class.pane__view--on]="view() === 'chart'"
                  [attr.aria-pressed]="view() === 'chart'"
                  (click)="view.set('chart')"
                  data-testid="pane-view-chart"
                >
                  {{ chartLabel() | transloco }}
                </button>
                <button
                  type="button"
                  [class.pane__view--on]="view() === 'table'"
                  [attr.aria-pressed]="view() === 'table'"
                  (click)="view.set('table')"
                  data-testid="pane-view-table"
                >
                  {{ 'charts.table' | transloco }}
                </button>
              </span>
            }
          </div>
        </div>
      }
      <div class="pane__body" [class.pane__body--flush]="flush()">
        <ng-content />
      </div>
    </section>
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
    }

    .pane {
      overflow: hidden;
      border-radius: var(--app-card-radius);
      background: var(--app-pane);
      border: 1px solid var(--app-pane-border);
      color: var(--mat-sys-on-surface);
    }

    .pane__head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 4px 10px;
      min-height: 36px;
      padding: 9px 12px 9px 18px;
      background: var(--app-pane-head);
      border-bottom: 2px solid var(--mat-sys-primary);
      font: 500 0.75rem / 1.3 var(--app-font-body);
      color: var(--app-muted);
    }

    .pane__heading {
      display: flex;
      align-items: center;
      gap: 8px;
      min-width: 0;
      margin: 0;
      font: 800 0.875rem / 1.3 var(--app-font-heading);
      color: var(--mat-sys-on-surface);
    }

    .pane__icon {
      --app-icon-size: 20px;
      flex: none;
      color: var(--mat-sys-primary);
    }

    .pane__meta {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px;
    }

    .pane__views {
      display: inline-flex;
      overflow: hidden;
      border: 1px solid var(--app-pane-border);
      border-radius: 999px;
      background: var(--app-pane);

      button {
        min-height: 34px;
        padding: 7px 12px;
        border: 0;
        background: transparent;
        color: var(--mat-sys-on-surface);
        font: 600 0.75rem / 1 var(--app-font-body);
        cursor: pointer;
      }

      button:focus-visible {
        outline: 2px solid var(--mat-sys-primary);
        outline-offset: -2px;
      }

      .pane__view--on {
        background: var(--app-ink);
        color: var(--app-gold-soft);
      }
    }

    .pane__body {
      display: grid;
      grid-template-columns: minmax(0, 1fr);
      gap: 12px;
      padding: 18px;
    }

    .pane__body--flush {
      gap: 0;
      padding: 0;
    }

    .pane--ink {
      background: var(--app-ink);
      border-color: var(--app-ink);
      color: var(--app-on-ink);

      .pane__head {
        background: transparent;
        border-color: var(--app-gold-soft);
        color: var(--app-on-ink-muted);
      }

      .pane__heading,
      .pane__icon {
        color: var(--app-gold-soft);
      }
    }

    .pane--brand {
      background: var(--app-brand-panel);
      border-color: var(--app-brand-panel);
      color: var(--app-on-brand-panel);

      .pane__head {
        background: transparent;
        border-color: var(--app-gold-soft);
        color: var(--app-on-brand-panel);
      }

      .pane__heading,
      .pane__icon {
        color: var(--app-gold-soft);
      }
    }

    @media (max-width: 599.98px) {
      .pane__body:not(.pane__body--flush) {
        padding: 16px;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaneComponent {
  /** The translated heading; without it the pane is a plain surface. */
  readonly heading = input<string | null>(null);
  /** The icon before the heading. */
  readonly icon = input<IconName | null>(null);
  /** The look of the pane. */
  readonly tone = input<PaneTone>('plain');
  /** True when the content brings its own padding (lists, grids, banners). */
  readonly flush = input(false, { transform: booleanAttribute });
  /** True when the widget has a table form even without a chart inside (cards with a table). */
  readonly tableable = input(false, { transform: booleanAttribute });
  /** The translation key of the first view, «Chart» unless the widget draws cards. */
  readonly chartLabel = input('charts.chart');
  /** The view the body shows; charts inside the pane follow it. */
  readonly view = signal<PaneView>('chart');
  protected readonly headingId = inject(_IdGenerator).getId('app-pane-');
  private readonly charts = signal(0);
  protected readonly switchable = computed(() => this.tableable() || this.charts() > 0);

  /**
   * Called by a chart inside the pane, so the heading offers the table form.
   *
   * @return the function the chart calls when it leaves the pane
   */
  holdChart(): () => void {
    this.charts.update((count) => count + 1);
    return () => this.charts.update((count) => count - 1);
  }
}
