import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../core/i18n/language.service';
import { CountUpDirective } from '../../../shared/effects/count-up.directive';
import { YearToDate } from '../../../shared/widgets/facts';
import { monthSpan } from '../../../shared/widgets/kpi-widget.component';
import { CalendarMonth } from '../../../shared/widgets/widget-data';
import { DeltaComponent } from '../../analytics/units/delta/delta.component';

const MONTHS = 12;
const RING_RADIUS = 50;
const RING_LENGTH = 2 * Math.PI * RING_RADIUS;

/** The months of a year that have begun: the current one included in the current year, all twelve before it. */
export function monthsBegun(year: number, today: CalendarMonth): number {
  if (year < today.year) {
    return MONTHS;
  }
  return year === today.year ? today.month : 0;
}

/** The page hero of the Hall of Fame: the year's count, a ring of the months passed and the year to date. */
@Component({
  selector: 'app-hall-of-fame-hero',
  imports: [CountUpDirective, DeltaComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="hero">
      <svg
        class="hero__ring"
        viewBox="0 0 120 120"
        role="img"
        data-testid="hero-ring"
        [attr.aria-label]="'hallOfFame.hero.ring' | transloco: { months: months(), total: 12 }"
      >
        <circle class="hero__ring-track" cx="60" cy="60" r="50" />
        <circle
          class="hero__ring-fill"
          cx="60"
          cy="60"
          r="50"
          transform="rotate(-90 60 60)"
          [attr.stroke-dasharray]="dash()"
        />
        <text class="hero__ring-value" x="60" y="58" text-anchor="middle">{{ months() }}/12</text>
        <text class="hero__ring-unit" x="60" y="76" text-anchor="middle">
          {{ 'hallOfFame.hero.months' | transloco }}
        </text>
      </svg>
      <div class="hero__body">
        <p class="hero__label">{{ 'hallOfFame.approvedIn' | transloco: { year: year() } }}</p>
        <p class="hero__count">
          <b [appCountUp]="value()" data-testid="kpi-value">0</b>
          @if (delta(); as ytd) {
            <app-delta class="hero__chip" [figure]="ytd" chip detailed data-testid="kpi-delta" />
          }
        </p>
        @if (delta(); as ytd) {
          <div class="hero__vs">
            <span>
              <b>{{ ytd.current }}</b>
              {{ span() }} {{ ytd.year }}
            </span>
            <span>
              <b>{{ ytd.previous }}</b>
              {{ span() }} {{ ytd.year - 1 }}
            </span>
          </div>
        }
      </div>
    </div>
  `,
  styles: `
    :host {
      display: block;

      --app-chip-bg: color-mix(in srgb, var(--app-on-brand-panel) 16%, transparent);
      --app-chip-color: var(--app-on-brand-panel);
    }

    .hero {
      display: grid;
      grid-template-columns: 130px minmax(0, 1fr);
      align-items: center;
      gap: 20px;
      padding: 18px 22px 22px;
    }

    .hero__ring {
      width: 130px;
      height: 130px;
    }

    .hero__ring-track,
    .hero__ring-fill {
      fill: none;
      stroke-width: 12;
    }

    .hero__ring-track {
      stroke: color-mix(in srgb, var(--app-gold-soft) 22%, transparent);
    }

    .hero__ring-fill {
      stroke: var(--app-gold-soft);
      stroke-linecap: round;
    }

    .hero__ring-value {
      font: 800 22px var(--app-font-heading);
      fill: currentcolor;
    }

    .hero__ring-unit {
      font: 500 10px var(--app-font-body);
      fill: currentcolor;
    }

    .hero__label {
      margin: 0 0 6px;
      font: 700 0.6875rem / 1.3 var(--app-font-heading);
      letter-spacing: 0.12em;
      text-transform: uppercase;
      color: var(--app-gold-soft);
    }

    .hero__count {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 12px;
      margin: 0;

      b {
        font: 800 3.25rem / 1 var(--app-font-heading);
        font-variant-numeric: tabular-nums;
      }
    }

    .hero__vs {
      display: flex;
      flex-wrap: wrap;
      gap: 6px 18px;
      margin-top: 12px;
      font: 400 0.75rem / 1.4 var(--app-font-body);

      b {
        display: block;
        font: 800 1.125rem / 1.2 var(--app-font-heading);
        font-variant-numeric: tabular-nums;
      }
    }

    @media (max-width: 599.98px) {
      .hero {
        grid-template-columns: 96px minmax(0, 1fr);
        gap: 14px;
        padding: 14px 16px 18px;
      }

      .hero__ring {
        width: 96px;
        height: 96px;
      }

      .hero__count b {
        font-size: 2.5rem;
      }
    }
  `,
})
export class HallOfFameHeroComponent {
  private readonly language = inject(LanguageService);

  /** The year the hero counts. */
  readonly year = input.required<number>();
  /** Approved awards of the year. */
  readonly value = input.required<number>();
  /** The current month in Kyiv. */
  readonly today = input.required<CalendarMonth>();
  /** The year to date against the same months of last year, or null to leave the comparison out. */
  readonly delta = input<YearToDate | null>(null);

  protected readonly months = computed(() => monthsBegun(this.year(), this.today()));
  protected readonly dash = computed(() => {
    const filled = (this.months() / MONTHS) * RING_LENGTH;
    return `${filled.toFixed(1)} ${RING_LENGTH.toFixed(1)}`;
  });
  protected readonly span = computed(() => {
    const ytd = this.delta();
    return ytd ? monthSpan(this.language.current(), ytd.months) : '';
  });
}
