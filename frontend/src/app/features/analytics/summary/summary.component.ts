import { Clipboard } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressBar } from '@angular/material/progress-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Store } from '@ngrx/store';
import { Observable, catchError, map, of } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { kyivToday } from '../../../shared/date-format';
import { KyivDatePipe } from '../../../shared/kyiv-date.pipe';
import { organizationName } from '../../../shared/organization-name';
import {
  AchievementsService,
  NO_ACHIEVEMENT_FILTERS,
} from '../../achievements/achievements.service';
import { awardTitle } from '../../awards/awards.service';
import { readPeriod } from '../analytics-period';
import { TimelineEntry } from '../analytics.service';
import { AnalyticsActions } from '../store/analytics.actions';
import { analyticsFeature } from '../store/analytics.feature';

/** Whether the public achievements of the account list an award, while asking and after a failure. */
type PublicCheck = 'checking' | 'shared' | 'none' | 'failed';

/** The printable summary of the counted awards of a period, with the link to the public achievements. */
@Component({
  selector: 'app-summary',
  imports: [KyivDatePipe, MatButton, MatIcon, MatProgressBar, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './summary.component.html',
  styleUrl: './summary.component.scss',
})
export class SummaryComponent {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly language = inject(LanguageService);
  private readonly clipboard = inject(Clipboard);
  private readonly achievements = inject(AchievementsService);

  protected readonly produced = kyivToday();
  protected readonly name = this.auth.fullName;
  protected readonly department = computed(() => {
    const organization = this.auth.profile()?.organization;
    return organization ? organizationName(organization, this.language.current()) : '';
  });

  readonly invalid = signal(false);
  readonly copied = signal(false);
  readonly period = this.store.selectSignal(analyticsFeature.selectPeriod);
  readonly analytics = this.store.selectSignal(analyticsFeature.selectAnalytics);
  readonly loading = this.store.selectSignal(analyticsFeature.selectLoading);
  readonly problem = this.store.selectSignal(analyticsFeature.selectProblem);
  readonly shared = toSignal(this.publicAwards(), { initialValue: 'checking' as PublicCheck });

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const period = readPeriod(params);
      this.invalid.set(period === null);
      if (period) {
        this.store.dispatch(AnalyticsActions.periodSelected({ period }));
      }
    });
  }

  protected title(entry: TimelineEntry): string {
    return awardTitle(entry, this.language.current());
  }

  protected print(): void {
    window.print();
  }

  protected copyLink(): void {
    const id = this.auth.profile()?.id;
    this.copied.set(
      id !== undefined &&
        this.clipboard.copy(`${location.origin}/public/achievements?person=${id}`),
    );
  }

  protected retry(): void {
    this.store.dispatch(AnalyticsActions.periodSelected({ period: this.period() }));
  }

  private publicAwards(): Observable<PublicCheck> {
    const id = this.auth.profile()?.id;
    if (id === undefined) {
      return of('none');
    }
    return this.achievements
      .list({ filters: { ...NO_ACHIEVEMENT_FILTERS, person: id }, page: 0, size: 1 }, 'public')
      .pipe(
        map((page): PublicCheck => (page.totalElements > 0 ? 'shared' : 'none')),
        catchError(() => of<PublicCheck>('failed')),
      );
  }
}
