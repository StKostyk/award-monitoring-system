import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { catchError, forkJoin, map, of } from 'rxjs';

import {
  OrganizationsService,
  OrganizationSummary,
} from '../../../core/organizations/organizations.service';
import { organizationName } from '../../../shared/organization-name';
import { AchievementScope } from '../achievements.service';

/** A unit linked from the header. */
export interface UnitLink {
  id: number;
  name: string;
}

interface Units {
  faculties: OrganizationSummary[];
  departments: OrganizationSummary[];
}

const NO_UNITS: Units = { faculties: [], departments: [] };

/** The name of a unit page with the way up to its faculty or down to its departments. */
@Component({
  selector: 'app-unit-header',
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './unit-header.component.html',
  styleUrl: './unit-header.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UnitHeaderComponent {
  private readonly transloco = inject(TranslocoService);
  private readonly organizations = inject(OrganizationsService);

  readonly unitId = input.required<number>();
  readonly scope = input<AchievementScope>('signed-in');
  /** The name the listed awards carry, for a unit no longer active. */
  readonly fallback = input<string | null>(null);

  private readonly language = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });
  private readonly units = toSignal(
    forkJoin([this.organizations.ofType('FACULTY'), this.organizations.ofType('DEPARTMENT')]).pipe(
      map(([faculties, departments]) => ({ faculties, departments })),
      catchError(() => of(NO_UNITS)),
    ),
    { initialValue: NO_UNITS },
  );
  private readonly unit = computed(() => {
    const { faculties, departments } = this.units();
    const id = this.unitId();
    return [...faculties, ...departments].find((unit) => unit.id === id) ?? null;
  });

  protected readonly base = computed(() =>
    this.scope() === 'public' ? '/public/units' : '/units',
  );
  protected readonly name = computed(() => {
    const unit = this.unit();
    return unit ? organizationName(unit, this.language()) : this.fallback();
  });
  protected readonly faculty = computed<UnitLink | null>(() => {
    const parent = this.unit()?.type === 'DEPARTMENT' ? this.unit()?.parent : null;
    return parent ? { id: parent.id, name: organizationName(parent, this.language()) } : null;
  });
  protected readonly departments = computed<UnitLink[]>(() => {
    const unit = this.unit();
    if (unit?.type !== 'FACULTY') {
      return [];
    }
    const language = this.language();
    return this.units()
      .departments.filter((department) => department.parent?.id === unit.id)
      .map((department) => ({ id: department.id, name: organizationName(department, language) }))
      .sort((a, b) => a.name.localeCompare(b.name, language));
  });
}
