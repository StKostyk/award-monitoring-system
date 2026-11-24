import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../../core/i18n/language.service';
import { organizationName } from '../../../../shared/organization-name';
import { UnitAnalytics } from '../unit-analytics.service';

const PODIUM = 3;

/** W2 A: the people with the most awards who agreed to be named, the first three on a podium and the rest ranked. */
@Component({
  selector: 'app-top-performers',
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './top-performers.component.html',
  styleUrl: './top-performers.component.scss',
})
export class TopPerformersComponent {
  private readonly language = inject(LanguageService);

  readonly topPerformers = input.required<UnitAnalytics['topPerformers']>();

  protected readonly people = computed(() => {
    const language = this.language.current();
    return this.topPerformers().people.map((person, index) => ({
      ...person,
      rank: index + 1,
      initials: initials(person.lastName, person.firstName),
      departmentName: person.department ? organizationName(person.department, language) : null,
    }));
  });
  protected readonly podium = computed(() => this.people().slice(0, PODIUM));
  protected readonly rest = computed(() => this.people().slice(PODIUM));
  protected readonly ranked = computed(() => this.people().length > 1);
}

function initials(...names: string[]): string {
  return names
    .map((name) => name.trim().charAt(0))
    .join('')
    .toLocaleUpperCase();
}
