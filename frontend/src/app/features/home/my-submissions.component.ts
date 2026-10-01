import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { MatAnchor } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../core/i18n/language.service';
import { Award, AwardsService, NO_FILTERS, awardTitle } from '../awards/awards.service';
import { RequestTimingComponent } from '../awards/request-timing/request-timing.component';

/** How many pending awards the card lists. */
export const SUBMISSIONS_SHOWN = 5;

/** The caller's newest awards under review with their level and expected date. */
@Component({
  selector: 'app-my-submissions',
  imports: [
    MatCard,
    MatCardHeader,
    MatCardTitle,
    MatCardContent,
    RequestTimingComponent,
    MatAnchor,
    RouterLink,
    TranslocoPipe,
  ],
  templateUrl: './my-submissions.component.html',
  styleUrl: './my-submissions.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MySubmissionsComponent implements OnInit {
  private readonly service = inject(AwardsService);
  private readonly language = inject(LanguageService);

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
