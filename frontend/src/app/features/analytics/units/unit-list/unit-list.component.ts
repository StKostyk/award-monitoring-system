import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../../core/i18n/language.service';
import { ListRowComponent, ListRowsDirective } from '../../../../shared/layout/list-row.component';
import { organizationName } from '../../../../shared/organization-name';
import { AnalysableUnit } from '../unit-analytics.service';

/**
 * The faculty of the shown unit as «Whole faculty» and then its departments open to the caller, each with its
 * approved awards once they are known; a row opens that unit's page for the same period.
 */
@Component({
  selector: 'app-unit-list',
  imports: [ListRowComponent, ListRowsDirective, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav
      class="picker"
      appListRows
      [attr.aria-label]="'unitAnalytics.unit' | transloco"
      data-testid="unit-picker"
    >
      @for (option of options(); track option.id) {
        <a
          appListRow
          [rowTitle]="option.whole ? ('unitAnalytics.wholeFaculty' | transloco) : option.title"
          [meta]="option.meta"
          [selected]="option.id === selected()"
          [routerLink]="['/analytics/units', option.id]"
          queryParamsHandling="preserve"
          [attr.data-testid]="'unit-option-' + option.id"
        >
          @if (option.count !== undefined) {
            <span rowEnd class="picker__count"
              >{{ option.count
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
    .picker {
      display: grid;
    }

    .picker__count {
      font: 800 1rem / 1 var(--app-font-heading);
      font-variant-numeric: tabular-nums;
      max-width: 120px;
    }
  `,
})
export class UnitListComponent {
  private readonly language = inject(LanguageService);

  readonly units = input.required<AnalysableUnit[]>();
  readonly selected = input<number | null>(null);
  /** Approved awards by unit id for the shown period, or null while they are unknown. */
  readonly counts = input<ReadonlyMap<number, number> | null>(null);

  protected readonly options = computed(() => {
    const language = this.language.current();
    const units = this.units();
    const counts = this.counts();
    const shown = units.find((unit) => unit.id === this.selected());
    if (!shown) {
      return [];
    }
    const facultyId = shown.type === 'FACULTY' ? shown.id : shown.parentId;
    const faculty = units.find((unit) => unit.id === facultyId);
    const departments = units.filter((unit) => unit.parentId === facultyId);
    const rows = departments.map((unit) => ({
      id: unit.id,
      title: organizationName(unit, language),
      meta: null as string | null,
      whole: false,
      count: counts?.get(unit.id),
    }));
    if (faculty) {
      rows.unshift({
        id: faculty.id,
        title: organizationName(faculty, language),
        meta: organizationName(faculty, language),
        whole: true,
        count: counts?.get(faculty.id),
      });
    }
    return rows;
  });
}
