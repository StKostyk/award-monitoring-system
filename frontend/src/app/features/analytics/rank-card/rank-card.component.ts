import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../core/i18n/language.service';
import { organizationName } from '../../../shared/organization-name';
import { PersonalRank } from '../analytics.service';

/** The place in the department, or why no place is shown. */
@Component({
  selector: 'app-rank-card',
  imports: [RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './rank-card.component.html',
})
export class RankCardComponent {
  private readonly language = inject(LanguageService);

  readonly rank = input.required<PersonalRank>();

  protected readonly department = computed(() => {
    const department = this.rank().department;
    return department ? organizationName(department, this.language.current()) : '';
  });
}
