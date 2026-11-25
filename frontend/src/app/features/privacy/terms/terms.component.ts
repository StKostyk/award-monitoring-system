import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatCard, MatCardContent } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { EMPTY, catchError, switchMap } from 'rxjs';

import { LanguageService } from '../../../core/i18n/language.service';

/** The version of the terms of use in force; its texts are `terms/<version>/<lang>.html`. */
export const TERMS_VERSION = '1.0';

/** The terms of use in the interface language, open to everyone, with a link to the privacy notice. */
@Component({
  selector: 'app-terms',
  imports: [MatCard, MatCardContent, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './terms.component.html',
  styleUrl: '../privacy-notice/privacy-notice.component.scss',
})
export class TermsComponent {
  private readonly http = inject(HttpClient);

  readonly text = signal<string | null>(null);
  readonly failed = signal(false);

  constructor() {
    const language = inject(LanguageService);
    toObservable(computed(() => language.current()))
      .pipe(
        switchMap((lang) => {
          this.failed.set(false);
          return this.http
            .get(`/terms/${TERMS_VERSION}/${lang}.html`, { responseType: 'text' })
            .pipe(
              catchError(() => {
                this.failed.set(true);
                this.text.set(null);
                return EMPTY;
              }),
            );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((html) => this.text.set(html));
  }
}
