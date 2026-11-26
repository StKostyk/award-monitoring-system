import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { LanguageService } from '../../../../core/i18n/language.service';
import { organizationName } from '../../../../shared/organization-name';
import { HighlightType, UnitHighlight } from '../unit-analytics.service';

const STRENGTHS: readonly HighlightType[] = ['GROWTH', 'ABOVE_AVERAGE'];

/**
 * W2 A: units that stand out as a big count, the previous count beside it and one line; strengths and gaps apart,
 * optionally capped with a button that expands the panel in place.
 */
@Component({
  selector: 'app-highlights',
  imports: [NgTemplateOutlet, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './highlights.component.html',
  styleUrl: './highlights.component.scss',
})
export class HighlightsComponent {
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  readonly highlights = input.required<UnitHighlight[]>();
  readonly sentenceKeys = input('unitAnalytics.highlights');
  /** The number shown before the panel is expanded; none shows every highlight. */
  readonly limit = input<number | null>(null);

  protected readonly expanded = signal(false);

  protected readonly sentences = computed(() => {
    const language = this.language.current();
    const prefix = this.sentenceKeys();
    return this.highlights().map((highlight) => ({
      strength: STRENGTHS.includes(highlight.type),
      key: `${highlight.type}-${highlight.unit.id}`,
      current: highlight.current,
      previous: highlight.previous,
      text: this.transloco.translate(`${prefix}.${sentenceKey(highlight)}`, {
        unit: organizationName(highlight.unit, language),
        current: highlight.current,
        previous: highlight.previous,
      }),
    }));
  });
  protected readonly capped = computed(() => {
    const limit = this.limit();
    return limit !== null && this.sentences().length > limit;
  });
  protected readonly shown = computed(() => {
    const limit = this.limit();
    return this.capped() && !this.expanded() && limit !== null
      ? this.sentences().slice(0, limit)
      : this.sentences();
  });
  protected readonly strengths = computed(() => this.shown().filter((entry) => entry.strength));
  protected readonly gaps = computed(() => this.shown().filter((entry) => !entry.strength));

  protected toggle(): void {
    this.expanded.update((expanded) => !expanded);
  }
}

function sentenceKey(highlight: UnitHighlight): string {
  return highlight.type === 'NO_AWARDS' && highlight.previous === 0
    ? 'NO_AWARDS_EVER'
    : highlight.type;
}
