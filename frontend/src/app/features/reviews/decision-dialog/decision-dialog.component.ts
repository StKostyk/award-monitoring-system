import { HttpStatusCode } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatError, MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatOption, MatSelect } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';
import { Observable, catchError, of } from 'rxjs';

import { problemStatus, problemType } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { ApprovalLevel } from '../../awards/awards.service';
import { reviewProblemKey, reviewerOf } from '../review-problems';
import { DecisionType, ReviewTemplate, ReviewsService } from '../reviews.service';
import { clearDraft, readDraft, writeDraft } from './decision-draft';

/** Longest comment the server accepts. */
export const COMMENT_MAX_LENGTH = 2000;

/**
 * The decision to confirm, the level an escalation goes to (null: the next level of each award) and whether the
 * award has documents; `count` is the number of awards of a batch. `draft` names where the typed comment is kept
 * for the signed-in user (the award id, or the awards of a batch); `submit` sends the decision, and the dialog
 * closes with its answer or stays open on a failure.
 */
export interface DecisionDialogData<R = unknown> {
  decision: DecisionType;
  target: ApprovalLevel | null;
  documents: number;
  count?: number;
  draft: string;
  submit: (input: DecisionInput) => Observable<R>;
}

/** What the reviewer entered. */
export interface DecisionInput {
  comment?: string;
  verified?: boolean;
}

/** Why a decision was not sent; a final problem leaves only closing the dialog. */
interface DecisionProblem {
  key: string;
  params?: Record<string, string>;
  final?: boolean;
}

/** Confirms a review decision with a comment, required for a return or a rejection, and sends it. */
@Component({
  selector: 'app-decision-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatButton,
    MatCheckbox,
    MatFormField,
    MatLabel,
    MatHint,
    MatError,
    MatInput,
    MatSelect,
    MatOption,
    MatProgressBar,
    TranslocoPipe,
  ],
  template: `
    <h2 mat-dialog-title data-testid="decision-title">
      {{
        'reviews.decide.title.' + data.decision
          | transloco: { level: ('reviews.decide.to.' + (data.target ?? 'higher') | transloco) }
      }}
    </h2>
    <mat-dialog-content class="decision">
      @if (data.count) {
        <p class="decision__count" data-testid="decision-count">
          {{ 'reviews.batch.count' | transloco: { count: data.count } }}
        </p>
      }
      @if (templates().length > 0) {
        <mat-form-field class="decision__comment" subscriptSizing="dynamic">
          <mat-label>{{ 'reviews.decide.template' | transloco }}</mat-label>
          <mat-select
            [formControl]="template"
            (selectionChange)="pick($event.value)"
            data-testid="decision-template"
          >
            @for (option of templates(); track option.id) {
              <mat-option [value]="option.id">{{ option.title }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      }
      @if (pending(); as replacing) {
        <div
          class="decision__replace"
          role="alertdialog"
          aria-labelledby="decision-replace"
          data-testid="decision-replace"
        >
          <span id="decision-replace">{{ 'reviews.decide.replace' | transloco }}</span>
          <button mat-button type="button" (click)="keep()" data-testid="decision-replace-no">
            {{ 'reviews.decide.replaceNo' | transloco }}
          </button>
          <button
            mat-flat-button
            type="button"
            (click)="fill(replacing)"
            data-testid="decision-replace-yes"
          >
            {{ 'reviews.decide.replaceYes' | transloco }}
          </button>
        </div>
      }
      <mat-form-field class="decision__comment" subscriptSizing="dynamic">
        <mat-label>{{
          (commentRequired ? 'reviews.decide.commentRequired' : 'reviews.decide.comment')
            | transloco
        }}</mat-label>
        <textarea
          matInput
          rows="4"
          [formControl]="comment"
          [maxlength]="maxLength"
          data-testid="decision-comment"
        ></textarea>
        <mat-hint>{{ 'reviews.decide.commentHint' | transloco }}</mat-hint>
        @if (comment.hasError('required')) {
          <mat-error>{{ 'reviews.decide.required' | transloco }}</mat-error>
        }
        @if (comment.hasError('maxlength')) {
          <mat-error>{{ 'reviews.decide.tooLong' | transloco }}</mat-error>
        }
      </mat-form-field>
      @if (data.decision === 'APPROVE' && data.documents > 0 && !data.count) {
        <mat-checkbox [formControl]="verified" data-testid="decision-verified">
          {{ 'reviews.decide.verified' | transloco }}
        </mat-checkbox>
      }
      @if (problem(); as shown) {
        <p class="decision__problem" role="alert" data-testid="decision-error">
          {{ shown.key | transloco: shown.params }}
        </p>
      }
      @if (sending()) {
        <mat-progress-bar
          mode="indeterminate"
          [attr.aria-label]="'reviews.decide.sending' | transloco"
          data-testid="decision-progress"
        />
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      @if (problem()?.final) {
        <button mat-flat-button type="button" (click)="close()" data-testid="decision-close">
          {{ 'reviews.decide.close' | transloco }}
        </button>
      } @else {
        <button
          mat-button
          type="button"
          [disabled]="sending()"
          (click)="close()"
          data-testid="decision-cancel"
        >
          {{ 'reviews.decide.cancel' | transloco }}
        </button>
        <button
          mat-flat-button
          type="button"
          [disabled]="sending()"
          (click)="confirm()"
          data-testid="decision-confirm"
        >
          {{ 'reviews.decide.confirm.' + data.decision | transloco }}
        </button>
      }
    </mat-dialog-actions>
  `,
  styles: `
    .decision {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .decision__comment {
      width: 100%;
    }

    .decision__count {
      margin: 0;
      font: var(--mat-sys-title-small);
    }

    .decision__problem {
      margin: 0;
      color: var(--mat-sys-error);
    }

    .decision__replace {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px;
      padding: 8px 12px;
      border-radius: 8px;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DecisionDialogComponent {
  readonly data = inject<DecisionDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<DecisionDialogComponent, unknown>>(MatDialogRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly self = inject(AuthService).userId();
  private readonly draft = `${this.self ?? ''}:${this.data.draft}`;

  readonly maxLength = COMMENT_MAX_LENGTH;
  readonly commentRequired = this.data.decision === 'RETURN' || this.data.decision === 'REJECT';
  readonly comment = new FormControl(readDraft(this.draft, this.data.decision), {
    nonNullable: true,
    validators: this.commentRequired
      ? [Validators.required, notBlank, Validators.maxLength(COMMENT_MAX_LENGTH)]
      : [Validators.maxLength(COMMENT_MAX_LENGTH)],
  });
  readonly verified = new FormControl(false, { nonNullable: true });
  readonly template = new FormControl<number | null>(null);
  readonly templates = toSignal(
    inject(ReviewsService)
      .templates(this.data.decision)
      .pipe(catchError(() => of<ReviewTemplate[]>([]))),
    { initialValue: [] },
  );
  /** A template waiting for the reviewer to confirm that it may replace their edited text. */
  readonly pending = signal<ReviewTemplate | null>(null);
  readonly sending = signal(false);
  readonly problem = signal<DecisionProblem | null>(null);
  private filled: { id: number; body: string } | null = null;

  constructor() {
    this.comment.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((value) => writeDraft(this.draft, this.data.decision, value));
    inject(DestroyRef).onDestroy(() => clearDraft(this.draft, this.data.decision));
  }

  /** Fills the comment with a template, asking first when that would replace text the reviewer wrote. */
  pick(id: number): void {
    const chosen = this.templates().find((option) => option.id === id);
    if (!chosen) {
      return;
    }
    const text = this.comment.value.trim();
    if (text && text !== this.filled?.body.trim()) {
      this.pending.set(chosen);
    } else {
      this.fill(chosen);
    }
  }

  fill(chosen: ReviewTemplate): void {
    this.comment.setValue(chosen.body);
    this.comment.markAsDirty();
    this.filled = { id: chosen.id, body: chosen.body };
    this.template.setValue(chosen.id);
    this.pending.set(null);
  }

  keep(): void {
    this.template.setValue(this.filled?.id ?? null);
    this.pending.set(null);
  }

  /** Sends the decision and keeps the dialog open until the answer. */
  confirm(): void {
    if (this.comment.invalid) {
      this.comment.markAsTouched();
      return;
    }
    const comment = this.comment.value.trim();
    this.sending.set(true);
    this.problem.set(null);
    this.ref.disableClose = true;
    this.data
      .submit({
        ...(comment ? { comment } : {}),
        ...(this.verified.value ? { verified: true } : {}),
      })
      .subscribe({
        next: (answer) => this.ref.close(answer),
        error: (error: unknown) => {
          this.sending.set(false);
          this.ref.disableClose = false;
          this.problem.set(explain(error, this.self));
          afterNextRender(() => this.focusAction(), { injector: this.injector });
        },
      });
  }

  /** Closes without a decision; the kept comment goes with the dialog. */
  close(): void {
    this.ref.close();
  }

  private focusAction(): void {
    this.host.nativeElement
      .querySelector<HTMLElement>(
        '[data-testid="decision-close"], [data-testid="decision-confirm"]',
      )
      ?.focus();
  }
}

function notBlank(control: AbstractControl<string>): ValidationErrors | null {
  return control.value.trim() ? null : { required: true };
}

function explain(error: unknown, self: string | null): DecisionProblem {
  const type = problemType(error);
  const status = problemStatus(error);
  if (type === 'request-claimed') {
    const holder = reviewerOf(error);
    return holder && String(holder.id) !== self
      ? { key: 'reviews.decide.errors.claimed', params: { name: holder.name } }
      : { key: 'reviews.decide.errors.stale' };
  }
  if (type === 'request-stale') {
    return { key: 'reviews.decide.errors.stale' };
  }
  if (type === 'request-closed') {
    return { key: 'reviews.decide.errors.closed', final: true };
  }
  if (status === HttpStatusCode.NotFound) {
    return { key: 'reviews.decide.errors.gone', final: true };
  }
  if (type === 'network' || status >= HttpStatusCode.InternalServerError) {
    return { key: 'reviews.decide.errors.network' };
  }
  return { key: reviewProblemKey(type) };
}
