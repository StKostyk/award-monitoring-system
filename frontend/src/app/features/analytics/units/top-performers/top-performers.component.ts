import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../../core/i18n/language.service';
import { organizationName } from '../../../../shared/organization-name';
import { UnitAnalytics } from '../unit-analytics.service';

/** The people with the most awards who agreed to be named, without positions; nobody when the group is too small. */
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
    return this.topPerformers().people.map((person) => ({
      ...person,
      departmentName: person.department ? organizationName(person.department, language) : null,
    }));
  });
}
