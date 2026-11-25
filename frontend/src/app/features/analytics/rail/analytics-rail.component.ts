import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  computed,
  inject,
  input,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { AuthService } from '../../../core/auth/auth.service';
import { canReadUniversityAnalytics } from '../../../core/auth/permissions';
import { LanguageService } from '../../../core/i18n/language.service';
import { STAY_IN_PLACE } from '../../../core/layout/navigation-focus.service';
import { ListRowComponent, ListRowsDirective } from '../../../shared/layout/list-row.component';
import { organizationName } from '../../../shared/organization-name';
import { AnalysableUnit } from '../units/unit-analytics.service';
import { topUnits } from './analytics-units';

interface RailRow {
  id: number;
  title: string;
  child: boolean;
  count: number | undefined;
}

/**
 * The side rail of the analytics pages: «Whole university» for a holder of the university view, the faculties
 * open to the caller and the departments of the open faculty beneath it; a row opens that page for the same period.
 */
@Component({
  selector: 'app-analytics-rail',
  imports: [ListRowComponent, ListRowsDirective, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav
      class="rail"
      appListRows
      [attr.aria-label]="'unitAnalytics.unit' | transloco"
      data-testid="unit-picker"
    >
      @if (university()) {
        <a
          appListRow
          icon="landmark"
          [rowTitle]="'unitAnalytics.wholeUniversity' | transloco"
          [selected]="universityPage()"
          wrap
          routerLink="/analytics/university"
          queryParamsHandling="preserve"
          data-testid="unit-option-university"
        ></a>
      }
      @for (row of rows(); track row.id) {
        <a
          appListRow
          [rowTitle]="row.title"
          [selected]="row.id === selected()"
          wrap
          [class.rail__child]="row.child"
          [routerLink]="['/analytics/units', row.id]"
          queryParamsHandling="preserve"
          [state]="stayInPlace"
          [attr.data-testid]="'unit-option-' + row.id"
        >
          @if (row.count !== undefined) {
            <span rowEnd class="rail__count"
              >{{ row.count
              }}<span class="cdk-visually-hidden">
                {{ 'unitAnalytics.approved' | transloco }}</span
              ></span
            >
          }
        </a>
      }
    </nav>
  `,
  styles: `
    .rail {
      display: grid;
    }

    .rail__child {
      padding-inline-start: 44px;
    }

    .rail__count {
      font: 800 1rem / 1 var(--app-font-heading);
      font-variant-numeric: tabular-nums;
      max-width: 120px;
    }
  `,
})
export class AnalyticsRailComponent {
  private readonly language = inject(LanguageService);
  private readonly auth = inject(AuthService);

  readonly units = input.required<AnalysableUnit[]>();
  /** The id of the shown unit, or null while none is shown. */
  readonly selected = input<number | null>(null);
  /** Whether the rail stands on the university page, which marks «Whole university» current. */
  readonly universityPage = input(false, { transform: booleanAttribute });
  /** Approved awards by unit id for the shown period, or null while they are unknown. */
  readonly counts = input<ReadonlyMap<number, number> | null>(null);

  protected readonly stayInPlace = { [STAY_IN_PLACE]: true };
  protected readonly university = computed(() =>
    canReadUniversityAnalytics(this.auth.permissions()),
  );

  protected readonly rows = computed<RailRow[]>(() => {
    const language = this.language.current();
    const units = this.units();
    const shown = units.find((unit) => unit.id === this.selected());
    const counts = shown ? this.counts() : null;
    const row = (unit: AnalysableUnit, child: boolean): RailRow => ({
      id: unit.id,
      title: organizationName(unit, language),
      child,
      count: counts?.get(unit.id),
    });
    return topUnits(units).flatMap((top) => {
      const open = shown !== undefined && (shown.id === top.id || shown.parentId === top.id);
      if (!open) {
        return [{ ...row(top, false), count: undefined }];
      }
      const children = units.filter((unit) => unit.parentId === top.id);
      return [row(top, false), ...children.map((unit) => row(unit, true))];
    });
  });
}
