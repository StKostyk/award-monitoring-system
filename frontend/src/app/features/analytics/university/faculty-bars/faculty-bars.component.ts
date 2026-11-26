import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../../core/i18n/language.service';
import { organizationName } from '../../../../shared/organization-name';
import { AnalyticsPeriod } from '../../analytics.service';
import { UnitComparisonRow } from '../../units/unit-analytics.service';

/** A closed range of years. */
export interface YearSpan {
  from: number;
  to: number;
}

/**
 * W3 A++ S1: the faculties and colleges by their approved awards, largest first; a thick bar for the period, a dark
 * tick at the previous period's count and the signed change beside the number.
 */
@Component({
  selector: 'app-faculty-bars',
  imports: [RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (bars().length > 0) {
      <ol class="bars">
        @for (bar of bars(); track bar.id) {
          <li class="bars__row" [attr.data-testid]="'faculty-bar-' + bar.id">
            <span class="bars__label">
              @if (bar.faculty) {
                <a [routerLink]="['/analytics/units', bar.id]" [queryParams]="query()">{{
                  bar.name
                }}</a>
              } @else {
                {{ bar.name }}
              }
            </span>
            <span class="bars__track" aria-hidden="true">
              <span class="bars__fill" [style.width.%]="bar.share"></span>
              @if (bar.tick !== null) {
                <span
                  class="bars__tick"
                  [style.left.%]="bar.tick"
                  data-testid="faculty-tick"
                ></span>
              }
            </span>
            <span class="bars__value">
              <b data-testid="faculty-count">{{ bar.count }}</b>
              <span class="bars__chip" data-testid="faculty-change">
                @if (bar.fresh) {
                  {{ 'unitAnalytics.delta.new' | transloco }}
                } @else {
                  {{ bar.change }}
                }
              </span>
              <span class="cdk-visually-hidden">
                {{ 'unitAnalytics.delta.previous' | transloco: { count: bar.previous } }}
              </span>
            </span>
          </li>
        }
      </ol>
      @if (ticked()) {
        <p class="bars__key" data-testid="faculty-key">
          <span class="bars__key-bar" aria-hidden="true"></span>{{ years(shown()) }}
          <span class="bars__key-tick" aria-hidden="true"></span>{{ years(previous()) }}
        </p>
      }
    } @else {
      <p class="analytics-note">{{ 'universityAnalytics.noFaculties' | transloco }}</p>
    }
  `,
  styles: `
    .bars {
      display: grid;
      gap: 0;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .bars__row {
      display: grid;
      grid-template-columns: minmax(0, 260px) minmax(0, 1fr) minmax(88px, auto);
      grid-template-areas: 'label track value';
      gap: 10px 12px;
      align-items: center;
      padding: 8px 0;
      font: 400 0.8125rem / 1.35 var(--app-font-body);
    }

    .bars__label {
      grid-area: label;
      overflow-wrap: anywhere;

      a {
        color: inherit;
        text-decoration: none;
      }

      a:hover {
        text-decoration: underline;
      }
    }

    .bars__track {
      grid-area: track;
      position: relative;
      height: 14px;
      border-radius: 7px;
      background: var(--app-track);
    }

    .bars__fill {
      position: absolute;
      inset: 0 auto 0 0;
      border-radius: 7px;
      background: var(--app-bar);
    }

    .bars__tick {
      position: absolute;
      top: -4px;
      bottom: -4px;
      width: 3px;
      margin-left: -1.5px;
      border-radius: 2px;
      background: var(--mat-sys-on-surface);
    }

    .bars__value {
      grid-area: value;
      display: inline-flex;
      justify-content: flex-end;
      align-items: center;
      gap: 6px;
      white-space: nowrap;

      b {
        font: 800 0.8125rem / 1 var(--app-font-heading);
        font-variant-numeric: tabular-nums;
      }
    }

    .bars__chip {
      padding: 3px 8px;
      border-radius: 999px;
      background: var(--app-pane-head);
      color: var(--app-bar);
      font: 700 0.6875rem / 1.3 var(--app-font-heading);
      font-variant-numeric: tabular-nums;
    }

    .bars__key {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px;
      margin: 12px 0 0;
      font: 400 0.75rem / 1.4 var(--app-font-body);
      color: var(--app-muted);
    }

    .bars__key-bar {
      width: 14px;
      height: 10px;
      border-radius: 5px;
      background: var(--app-bar);
    }

    .bars__key-tick {
      width: 3px;
      height: 14px;
      margin-left: 10px;
      border-radius: 2px;
      background: var(--mat-sys-on-surface);
    }

    @media (max-width: 599.98px) {
      .bars__row {
        grid-template-columns: minmax(0, 1fr) auto;
        grid-template-areas: 'label value' 'track track';
        gap: 6px 10px;
      }
    }
  `,
})
export class FacultyBarsComponent {
  private readonly language = inject(LanguageService);

  readonly rows = input.required<UnitComparisonRow[]>();
  /** The shown period, carried to a faculty's page. */
  readonly period = input.required<AnalyticsPeriod>();
  /** The years of the shown period, for the key. */
  readonly shown = input.required<YearSpan>();
  /** The years of the previous period, for the key. */
  readonly previous = input.required<YearSpan>();

  protected readonly query = computed(() => ({ from: this.period().from, to: this.period().to }));
  protected readonly ticked = computed(() =>
    this.rows().some((row) => !row.facultyLevel && row.approved.previous > 0),
  );
  protected readonly bars = computed(() => {
    const language = this.language.current();
    const ticked = this.ticked();
    const rows = this.rows()
      .filter((row) => !row.facultyLevel)
      .sort((a, b) => b.approved.current - a.approved.current);
    const largest = Math.max(
      1,
      ...rows.map((row) => Math.max(row.approved.current, row.approved.previous)),
    );
    return rows.map((row) => {
      const { current, previous } = row.approved;
      return {
        id: row.unit.id,
        name: organizationName(row.unit, language),
        count: current,
        previous,
        share: (current / largest) * 100,
        tick: ticked && previous > 0 ? (previous / largest) * 100 : null,
        fresh: previous === 0 && current > 0,
        change: signed(current - previous),
        faculty: row.unit.type === 'FACULTY',
      };
    });
  });

  protected years(span: YearSpan): string {
    return span.from === span.to ? String(span.from) : `${span.from}–${span.to}`;
  }
}

function signed(change: number): string {
  if (change > 0) {
    return `+${change}`;
  }
  return change < 0 ? `−${-change}` : '0';
}
