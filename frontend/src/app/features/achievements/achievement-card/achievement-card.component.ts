import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../core/i18n/language.service';
import { IconComponent } from '../../../shared/icons/icon.component';
import { KyivDatePipe } from '../../../shared/kyiv-date.pipe';
import { organizationName } from '../../../shared/organization-name';
import { awardTitle, categoryName } from '../../awards/awards.service';
import { Achievement } from '../achievements.service';
import { initials } from '../../../shared/widgets/top-widget.component';

@Component({
  selector: 'app-achievement-card',
  imports: [IconComponent, RouterLink, TranslocoPipe, KyivDatePipe],
  templateUrl: './achievement-card.component.html',
  styleUrl: './achievement-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AchievementCardComponent {
  private readonly language = inject(LanguageService);

  readonly achievement = input.required<Achievement>();
  /** Where unit pages live: the signed-in ones or the public ones. */
  readonly unitPages = input('/units');
  /** Whether the title opens the colleague view of the award. */
  readonly linked = input(false);
  /** A feed row, or the burgundy banner of the highlight of the month. */
  readonly variant = input<'row' | 'hero'>('row');

  protected readonly title = computed(() =>
    awardTitle(this.achievement(), this.language.current()),
  );
  protected readonly description = computed(() => {
    const achievement = this.achievement();
    return this.language.current() === 'en'
      ? (achievement.description ?? achievement.descriptionUk)
      : (achievement.descriptionUk ?? achievement.description);
  });
  protected readonly unit = computed(() =>
    organizationName(this.achievement().recipient.unit, this.language.current()),
  );
  protected readonly initials = computed(() => {
    const recipient = this.achievement().recipient;
    return initials(recipient.type === 'PERSON' ? (recipient.personName ?? '') : this.unit());
  });
  protected readonly category = computed(() =>
    categoryName(this.achievement().category, this.language.current()),
  );
}
