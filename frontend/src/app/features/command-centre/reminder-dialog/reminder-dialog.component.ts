import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatOption, MatSelect } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';

import { knownProblem, problemStatus, problemType } from '../../../core/api/problem';
import { OrganizationRef } from '../../../core/auth/user-profile';
import { LanguageService } from '../../../core/i18n/language.service';
import { organizationName } from '../../../shared/organization-name';
import { UserSummary } from '../../admin/users.service';
import { CommandCentreService } from '../command-centre.service';

/** The department to remind and the year without awards. */
export interface ReminderDialogData {
  unit: OrganizationRef;
  year: number;
}

/** When the department was reminded: by this dialog, or earlier by somebody else. */
export interface ReminderResult {
  sentAt: string;
}

/** Problems with a message of their own; anything else is `unknown`. */
const KNOWN = ['has-awards', 'invalid-recipient', 'denied', 'network'];
const RECENTLY_REMINDED = 'recently-reminded';

/** «Нагадати завідувачу»: picks an active colleague of the department and sends the reminder. */
@Component({
  selector: 'app-reminder-dialog',
  imports: [
    MatButton,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MatError,
    MatFormField,
    MatLabel,
    MatOption,
    MatProgressBar,
    MatSelect,
    ReactiveFormsModule,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ 'commandCentre.reminder.title' | transloco }}</h2>
    <mat-dialog-content>
      <p class="reminder__text">
        {{ 'commandCentre.reminder.text' | transloco: { unit: unitName, year: data.year } }}
      </p>
      @if (loading()) {
        <mat-progress-bar mode="indeterminate" [attr.aria-label]="'app.loading' | transloco" />
      } @else if (members().length === 0) {
        <p data-testid="reminder-nobody">{{ 'commandCentre.reminder.nobody' | transloco }}</p>
      } @else {
        <mat-form-field class="reminder__field">
          <mat-label>{{ 'commandCentre.reminder.recipient' | transloco }}</mat-label>
          <mat-select [formControl]="recipient" data-testid="reminder-recipient">
            @for (member of members(); track member.id) {
              <mat-option [value]="member.id">
                {{ member.lastName }} {{ member.firstName }}
              </mat-option>
            }
          </mat-select>
          <mat-error>{{ 'commandCentre.reminder.required' | transloco }}</mat-error>
        </mat-form-field>
      }
      @if (problem(); as key) {
        <p class="reminder__problem" role="alert" data-testid="reminder-problem">
          {{ 'commandCentre.reminder.problems.' + key | transloco }}
        </p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>
        {{ 'commandCentre.reminder.cancel' | transloco }}
      </button>
      <button
        mat-flat-button
        type="button"
        data-testid="reminder-send"
        [disabled]="sending() || members().length === 0"
        (click)="send()"
      >
        {{ 'commandCentre.reminder.send' | transloco }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .reminder__field {
      width: 100%;
    }

    .reminder__problem {
      color: var(--mat-sys-error);
    }
  `,
})
export class ReminderDialogComponent {
  private readonly service = inject(CommandCentreService);
  private readonly dialogRef =
    inject<MatDialogRef<ReminderDialogComponent, ReminderResult>>(MatDialogRef);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly data = inject<ReminderDialogData>(MAT_DIALOG_DATA);
  protected readonly unitName = organizationName(this.data.unit, inject(LanguageService).current());
  protected readonly recipient = new FormControl<number | null>(null, Validators.required);
  protected readonly members = signal<UserSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly sending = signal(false);
  /** The message key of the last refusal. */
  protected readonly problem = signal<string | null>(null);

  constructor() {
    this.service
      .members(this.data.unit.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (members) => {
          this.members.set(members);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.problem.set(problemKey(error));
        },
      });
  }

  protected send(): void {
    const recipientId = this.recipient.value;
    if (recipientId === null) {
      this.recipient.markAsTouched();
      return;
    }
    this.sending.set(true);
    this.problem.set(null);
    this.service
      .remind(this.data.unit.id, recipientId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (reminder) => this.dialogRef.close({ sentAt: reminder.sentAt }),
        error: (error: unknown) => {
          this.sending.set(false);
          const lastSentAt = recentReminder(error);
          if (lastSentAt) {
            this.dialogRef.close({ sentAt: lastSentAt });
            return;
          }
          this.problem.set(problemKey(error));
        },
      });
  }
}

/** The date of the last reminder of a 409 `recently-reminded`, else null. */
function recentReminder(error: unknown): string | null {
  if (problemType(error) !== RECENTLY_REMINDED || !(error instanceof HttpErrorResponse)) {
    return null;
  }
  const lastSentAt = (error.error as { lastSentAt?: unknown } | null)?.lastSentAt;
  return typeof lastSentAt === 'string' ? lastSentAt : null;
}

function problemKey(error: unknown): string {
  const type = problemStatus(error) === HttpStatusCode.Forbidden ? 'denied' : problemType(error);
  return knownProblem(type, KNOWN);
}
