import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, forkJoin, of } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { approvalScopes } from '../../../core/auth/permissions';
import { ReviewPeriodDialogComponent } from '../review-period-dialog/review-period-dialog.component';
import { ReviewPeriod, ReviewsService } from '../reviews.service';

const FACULTY_ROLES = ['FACULTY_SECRETARY', 'DEAN'];
const PLURALS = new Intl.PluralRules('uk');

/** The review period of each faculty the caller reviews for, with «Змінити» for its dean. */
@Component({
  selector: 'app-review-period',
  imports: [MatButton, TranslocoPipe],
  template: `
    @for (period of periods(); track period.organizationId) {
      <p class="review-period" data-testid="review-period">
        <span data-testid="review-period-text">
          {{
            'reviews.period.label'
              | transloco
                : {
                    days:
                      ('reviews.period.days.' + plural(period.effectiveWorkingDays)
                      | transloco: { count: period.effectiveWorkingDays }),
                  }
          }}
          @if (period.workingDays === null) {
            {{ 'reviews.period.default' | transloco }}
          }
        </span>
        @if (period.updatable) {
          <button
            mat-button
            type="button"
            (click)="change(period)"
            data-testid="review-period-change"
          >
            {{ 'reviews.period.change' | transloco }}
          </button>
        }
      </p>
    }
  `,
  styles: `
    .review-period {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 4px 8px;
      margin: 0 0 8px;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReviewPeriodComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly reviews = inject(ReviewsService);
  private readonly dialog = inject(MatDialog);

  readonly periods = signal<ReviewPeriod[]>([]);

  ngOnInit(): void {
    const faculties = [
      ...new Set(
        approvalScopes(this.auth.permissions())
          .filter((scope) => FACULTY_ROLES.includes(scope.role))
          .map((scope) => scope.organizationId),
      ),
    ];
    if (faculties.length === 0) {
      return;
    }
    forkJoin(
      faculties.map((id) => this.reviews.reviewPeriod(id).pipe(catchError(() => of(null)))),
    ).subscribe((periods) =>
      this.periods.set(periods.filter((period): period is ReviewPeriod => period !== null)),
    );
  }

  plural(count: number): string {
    return PLURALS.select(count);
  }

  change(period: ReviewPeriod): void {
    this.dialog
      .open<ReviewPeriodDialogComponent, ReviewPeriod, ReviewPeriod>(ReviewPeriodDialogComponent, {
        data: period,
        width: '420px',
      })
      .afterClosed()
      .subscribe((changed) => {
        if (changed) {
          this.periods.update((periods) =>
            periods.map((item) =>
              item.organizationId === changed.organizationId ? changed : item,
            ),
          );
        }
      });
  }
}
