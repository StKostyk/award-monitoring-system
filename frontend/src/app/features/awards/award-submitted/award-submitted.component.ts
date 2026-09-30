import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressBar } from '@angular/material/progress-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { Award, AwardsService, awardTitle } from '../awards.service';

/** Confirms the caller's own submitted award; any other award or id is not a submission to confirm. */
@Component({
  selector: 'app-award-submitted',
  imports: [RouterLink, MatButton, MatIcon, MatProgressBar, TranslocoPipe],
  template: `
    @if (loading()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (notFound()) {
      <p class="award-submitted__problem" role="alert" data-testid="award-not-found">
        {{ 'awards.notFound' | transloco }}
      </p>
    }
    @if (award(); as award) {
      <section class="award-submitted" role="status" data-testid="award-submitted">
        <mat-icon class="award-submitted__icon" aria-hidden="true">task_alt</mat-icon>
        <h1 class="award-submitted__title">{{ 'awards.submitted.title' | transloco }}</h1>
        <p class="award-submitted__name" data-testid="award-submitted-name">«{{ name(award) }}»</p>
        <p data-testid="award-submitted-text">{{ 'awards.submitted.text' | transloco }}</p>
        <div class="award-submitted__actions">
          <a mat-flat-button routerLink="/awards" data-testid="award-submitted-list">
            {{ 'awards.submitted.toList' | transloco }}
          </a>
          <a mat-button routerLink="/awards/new">{{ 'awards.add' | transloco }}</a>
        </div>
      </section>
    }
  `,
  styles: `
    .award-submitted {
      max-width: 560px;
      text-align: center;
      margin: 24px auto;
    }
    .award-submitted__icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
      color: var(--mat-sys-primary, #1b5e9e);
    }
    .award-submitted__title {
      font: var(--mat-sys-headline-small);
    }
    .award-submitted__name {
      font: var(--mat-sys-title-medium);
      overflow-wrap: anywhere;
    }
    .award-submitted__actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      gap: 12px;
    }
    .award-submitted__problem {
      color: var(--mat-sys-error, #b3261e);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AwardSubmittedComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(AwardsService);
  private readonly auth = inject(AuthService);
  private readonly language = inject(LanguageService);

  readonly award = signal<Award | null>(null);
  readonly loading = signal(false);
  readonly notFound = signal(false);

  ngOnInit(): void {
    const param = this.route.snapshot.paramMap.get('id') ?? '';
    if (!/^\d+$/.test(param)) {
      this.notFound.set(true);
      return;
    }
    this.loading.set(true);
    this.service.get(Number(param)).subscribe({
      next: (award) => this.show(award),
      error: () => {
        this.loading.set(false);
        this.notFound.set(true);
      },
    });
  }

  name(award: Award): string {
    return awardTitle(award, this.language.current());
  }

  private show(award: Award): void {
    this.loading.set(false);
    if (String(award.owner.id) !== this.auth.userId()) {
      void this.router.navigate(['/awards', award.id], { replaceUrl: true });
    } else if (award.status === 'DRAFT') {
      void this.router.navigate(['/awards', award.id, 'edit'], { replaceUrl: true });
    } else {
      this.award.set(award);
    }
  }
}
