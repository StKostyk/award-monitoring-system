import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatProgressBar } from '@angular/material/progress-bar';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Store } from '@ngrx/store';

import { AuthService } from '../../../../core/auth/auth.service';
import { canReadUniversityAnalytics } from '../../../../core/auth/permissions';
import { landingUnit } from '../../rail/analytics-units';
import { UnitAnalyticsActions } from '../store/unit-analytics.actions';
import { unitAnalyticsFeature } from '../store/unit-analytics.feature';

/**
 * `/analytics`: opens the university page for a holder of the university view, else the first faculty open to the
 * caller (the first unit when no faculty is), keeping the period of the address bar.
 */
@Component({
  selector: 'app-unit-analytics-entry',
  imports: [MatButton, MatProgressBar, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="entry__title">{{ 'unitAnalytics.title' | transloco }}</h1>
    @switch (status()) {
      @case ('failed') {
        <div class="entry__problem" role="alert" data-testid="unit-analytics-units-error">
          <p>{{ 'unitAnalytics.unitsFailed' | transloco }}</p>
          <button mat-stroked-button type="button" (click)="retry()">
            {{ 'analytics.retry' | transloco }}
          </button>
        </div>
      }
      @case ('loaded') {
        @if (units().length === 0) {
          <p data-testid="unit-analytics-no-units">{{ 'unitAnalytics.noUnits' | transloco }}</p>
        }
      }
      @default {
        <mat-progress-bar mode="indeterminate" [attr.aria-label]="'app.loading' | transloco" />
      }
    }
  `,
  styles: `
    .entry__title {
      font: var(--mat-sys-headline-small);
      margin: 0 0 16px;
    }

    .entry__problem {
      color: var(--mat-sys-error);
    }
  `,
})
export class UnitAnalyticsEntryComponent {
  private readonly store = inject(Store);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly university = canReadUniversityAnalytics(inject(AuthService).permissions());

  readonly units = this.store.selectSignal(unitAnalyticsFeature.selectUnits);
  readonly status = this.store.selectSignal(unitAnalyticsFeature.selectUnitsStatus);

  constructor() {
    if (this.university) {
      this.open(['university']);
      return;
    }
    if (this.status() !== 'loaded') {
      this.store.dispatch(UnitAnalyticsActions.unitsRequested());
    }
    effect(() => {
      const unit = landingUnit(this.units());
      if (this.status() === 'loaded' && unit) {
        this.open(['units', unit.id]);
      }
    });
  }

  protected retry(): void {
    this.store.dispatch(UnitAnalyticsActions.unitsRequested());
  }

  private open(commands: (string | number)[]): void {
    void this.router.navigate(commands, {
      relativeTo: this.route,
      queryParamsHandling: 'preserve',
      replaceUrl: true,
    });
  }
}
