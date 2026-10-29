import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent } from '@angular/material/card';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatOption, MatSelect } from '@angular/material/select';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { EMPTY, catchError, filter, switchMap } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { fieldProblems, knownProblem, problemType } from '../../../core/api/problem';
import { LanguageService } from '../../../core/i18n/language.service';
import { KyivDatePipe } from '../../../shared/kyiv-date.pipe';
import { localPath } from '../../../shared/local-path';
import { ConsentsService, PrivacyNotice } from '../consents.service';

/** Problems of the acknowledgement with a message of their own. */
const ACKNOWLEDGE_PROBLEMS = ['outdated', 'network'];

@Component({
  selector: 'app-privacy-notice',
  imports: [
    MatButton,
    MatCard,
    MatCardContent,
    MatFormField,
    MatLabel,
    MatSelect,
    MatOption,
    TranslocoPipe,
    KyivDatePipe,
  ],
  templateUrl: './privacy-notice.component.html',
  styleUrl: './privacy-notice.component.scss',
})
export class PrivacyNoticeComponent implements OnInit {
  private readonly consents = inject(ConsentsService);
  private readonly language = inject(LanguageService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);

  readonly notice = signal<PrivacyNotice | null>(null);
  readonly version = signal<string | null>(null);
  readonly text = signal<string | null>(null);
  readonly failed = signal(false);
  readonly error = signal<string | null>(null);
  readonly sending = signal(false);
  readonly acknowledging = computed(
    () =>
      this.auth.isAuthenticated() &&
      this.route.snapshot.queryParamMap.get('acknowledge') === 'true',
  );

  constructor() {
    const shown = computed(() => ({ version: this.version(), lang: this.language.current() }));
    toObservable(shown)
      .pipe(
        filter((choice) => choice.version !== null),
        switchMap(({ version, lang }) => {
          this.failed.set(false);
          return this.consents.noticeText(version as string, lang).pipe(
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

  ngOnInit(): void {
    this.load();
  }

  acknowledge(): void {
    const current = this.notice()?.version;
    if (!current || this.sending()) {
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    this.consents.acknowledge(current).subscribe({
      next: () =>
        void this.router.navigateByUrl(
          localPath(this.route.snapshot.queryParamMap.get('returnUrl')),
        ),
      error: (err: unknown) => {
        const outdated = fieldProblems(err).some((problem) => problem.code === 'outdated');
        this.error.set(
          `privacy.notice.errors.${outdated ? 'outdated' : knownProblem(problemType(err), ACKNOWLEDGE_PROBLEMS)}`,
        );
        this.sending.set(false);
        if (outdated) {
          this.load();
        }
      },
    });
  }

  signOut(): void {
    void this.auth.logout();
  }

  private load(): void {
    this.consents.notice().subscribe({
      next: (notice) => {
        this.notice.set(notice);
        this.version.set(notice.version);
      },
      error: () => this.failed.set(true),
    });
  }
}
