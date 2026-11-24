import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../../core/i18n/language.service';
import { organizationName } from '../../../../shared/organization-name';
import { AnalyticsPeriod } from '../../analytics.service';
import { UnitComparisonRow } from '../../units/unit-analytics.service';

/** The faculties and colleges by their approved awards, largest first, each bar sized against the largest count. */
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
            <span class="bars__value">{{ bar.count }}</span>
            <span class="bars__track" aria-hidden="true">
              <span class="bars__fill" [style.width.%]="bar.share"></span>
            </span>
          </li>
        }
      </ol>
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
      grid-template-columns: minmax(0, 260px) minmax(0, 1fr) 40px;
      grid-template-areas: 'label track value';
      gap: 10px;
      align-items: center;
      padding: 7px 0;
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

    .bars__value {
      grid-area: value;
      text-align: end;
      font: 700 0.8125rem / 1 var(--app-font-heading);
      font-variant-numeric: tabular-nums;
    }

    .bars__track {
      grid-area: track;
      height: 10px;
      border-radius: 5px;
      background: var(--app-track);
      overflow: hidden;
    }

    .bars__fill {
      display: block;
      height: 100%;
      border-radius: 5px;
      background: var(--app-bar-gold);
    }

    @media (max-width: 599.98px) {
      .bars__row {
        grid-template-columns: minmax(0, 1fr) 40px;
        grid-template-areas: 'label value' 'track track';
        gap: 4px 10px;
      }
    }
  `,
})
export class FacultyBarsComponent {
  private readonly language = inject(LanguageService);

  readonly rows = input.required<UnitComparisonRow[]>();
  /** The shown period, carried to a faculty's page. */
  readonly period = input.required<AnalyticsPeriod>();

  protected readonly query = computed(() => ({ from: this.period().from, to: this.period().to }));
  protected readonly bars = computed(() => {
    const language = this.language.current();
    const rows = this.rows()
      .filter((row) => !row.facultyLevel)
      .sort((a, b) => b.approved.current - a.approved.current);
    const largest = Math.max(1, ...rows.map((row) => row.approved.current));
    return rows.map((row) => ({
      id: row.unit.id,
      name: organizationName(row.unit, language),
      count: row.approved.current,
      share: (row.approved.current / largest) * 100,
      faculty: row.unit.type === 'FACULTY',
    }));
  });
}
