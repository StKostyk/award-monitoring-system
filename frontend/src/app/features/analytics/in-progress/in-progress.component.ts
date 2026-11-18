import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { MatAnchor } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../core/i18n/language.service';
import { Award, AwardsService, NO_FILTERS, awardTitle } from '../../awards/awards.service';
import { RequestTimingComponent } from '../../awards/request-timing/request-timing.component';

/** How many pending awards the widget lists. */
export const SUBMISSIONS_SHOWN = 5;

/** The caller's drafts and pending awards: their counts and the newest pending ones with level and expected date. */
@Component({
  selector: 'app-in-progress',
  imports: [MatAnchor, RequestTimingComponent, RouterLink, TranslocoPipe],
  templateUrl: './in-progress.component.html',
  styleUrl: './in-progress.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InProgressComponent implements OnInit {
  private readonly service = inject(AwardsService);
  private readonly language = inject(LanguageService);

  readonly drafts = input(0);
  readonly pending = input(0);
  readonly canCreate = input(false);

  readonly awards = signal<Award[] | null>(null);
  readonly failed = signal(false);

  ngOnInit(): void {
    this.service.list({ ...NO_FILTERS, status: 'PENDING' }, 0, SUBMISSIONS_SHOWN).subscribe({
      next: (page) => this.awards.set(page.content),
      error: () => this.failed.set(true),
    });
  }

  title(award: Award): string {
    return awardTitle(award, this.language.current());
  }
}
