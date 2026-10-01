import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { MatChip } from '@angular/material/chips';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../core/i18n/language.service';
import { kyivDate } from '../../../shared/date-format';
import { AwardRequestSummary } from '../awards.service';

/**
 * Where a request stands in one line: waiting for the owner's corrections, or the current level and the expected
 * completion; a delayed request also gets a chip.
 */
@Component({
  selector: 'app-request-timing',
  imports: [MatChip, TranslocoPipe],
  templateUrl: './request-timing.component.html',
  styleUrl: './request-timing.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RequestTimingComponent {
  readonly request = input.required<AwardRequestSummary>();
  /** Whether the current level is named before the expected completion. */
  readonly showLevel = input(false);

  private readonly language = inject(LanguageService);

  protected readonly returned = computed(() => this.request().status === 'RETURNED');
  protected readonly expected = computed(() => {
    const estimate = this.request().estimatedCompletion;
    return estimate ? kyivDate(estimate, this.language.current()) : null;
  });
}
