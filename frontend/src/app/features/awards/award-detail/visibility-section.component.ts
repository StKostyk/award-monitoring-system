import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
  output,
  signal,
} from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatRadioButton, MatRadioGroup } from '@angular/material/radio';
import { TranslocoPipe } from '@jsverse/transloco';
import { Observable, of } from 'rxjs';

import { knownProblem, problemType } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { canEditOwnAwards } from '../../../core/auth/permissions';
import { Award, AwardVisibility, AwardsService, VISIBILITIES } from '../awards.service';
import { confirmPublication } from './publish-dialog.component';

/** Refusals with a message of their own. */
const KNOWN_PROBLEMS = ['visibility-fixed', 'access-denied', 'network'];

@Component({
  selector: 'app-visibility-section',
  imports: [MatRadioGroup, MatRadioButton, TranslocoPipe],
  template: `
    <section class="visibility" data-testid="award-visibility">
      <h2 class="visibility__title" id="award-visibility-title">
        {{ 'awards.visibility.title' | transloco }}
      </h2>
      <mat-radio-group
        class="visibility__options"
        aria-labelledby="award-visibility-title"
        [value]="current()"
        [disabled]="saving() || !canChange()"
        (change)="choose($event.value)"
      >
        @for (option of options; track option) {
          <mat-radio-button [value]="option" [attr.data-testid]="'visibility-' + option">
            {{ 'awards.visibility.options.' + option | transloco }}
          </mat-radio-button>
        }
      </mat-radio-group>
      <p class="visibility__hint" data-testid="visibility-hint">
        {{ 'awards.visibility.hints.' + current() | transloco }}
      </p>
      @if (notice(); as notice) {
        <p class="visibility__notice" role="status" data-testid="visibility-notice">
          {{ notice | transloco }}
        </p>
      }
    </section>
  `,
  styles: `
    .visibility__title {
      margin: 0 0 8px;
      font: var(--mat-sys-title-medium);
    }

    .visibility__options {
      display: flex;
      flex-wrap: wrap;
      gap: 0 16px;
    }

    .visibility__hint,
    .visibility__notice {
      margin: 4px 0 0;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VisibilitySectionComponent {
  private readonly dialog = inject(MatDialog);
  private readonly service = inject(AwardsService);
  private readonly auth = inject(AuthService);

  /** The caller's approved personal award. */
  readonly award = input.required<Award>();
  /** The award as saved with its new visibility. */
  readonly changed = output<Award>();
  /** The change was refused; the award may have changed meanwhile. */
  readonly refused = output();

  protected readonly options = VISIBILITIES;
  protected readonly current = linkedSignal<AwardVisibility>(
    () => this.award().visibility ?? 'PRIVATE',
  );
  protected readonly canChange = computed(() => canEditOwnAwards(this.auth.permissions()));
  protected readonly saving = signal(false);
  protected readonly notice = signal<string | null>(null);

  /** Saves the chosen visibility; going public asks first, and cancelling keeps the current choice. */
  choose(visibility: AwardVisibility): void {
    this.current.set(visibility);
    this.notice.set(null);
    this.confirm(visibility).subscribe((confirmed) => {
      if (confirmed) {
        this.save(visibility);
      } else {
        this.current.set(this.award().visibility ?? 'PRIVATE');
      }
    });
  }

  private confirm(visibility: AwardVisibility): Observable<boolean> {
    return visibility === 'PUBLIC' ? confirmPublication(this.dialog) : of(true);
  }

  private save(visibility: AwardVisibility): void {
    const award = this.award();
    this.saving.set(true);
    this.service.updateVisibility(award.id, visibility).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.notice.set('awards.visibility.saved');
        this.changed.emit(saved);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.current.set(award.visibility ?? 'PRIVATE');
        this.notice.set(`awards.problems.${knownProblem(problemType(error), KNOWN_PROBLEMS)}`);
        this.refused.emit();
      },
    });
  }
}
