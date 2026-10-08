import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';

import { ShownValue } from '../award-history/version-values';

/** One corrected field as shown in the confirmation. */
export interface ShownChange {
  label: string;
  from: ShownValue;
  to: ShownValue;
}

/** The changes and the reason a reviewer is about to send. */
export interface CorrectionDialogData {
  changes: ShownChange[];
  reason: string;
}

/** Lists every corrected field with its old and new value before the correction is sent. */
@Component({
  selector: 'app-correction-dialog',
  imports: [
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatDialogClose,
    MatButton,
    TranslocoPipe,
  ],
  template: `
    <h2 mat-dialog-title>{{ 'awards.correction.confirm.title' | transloco }}</h2>
    <mat-dialog-content>
      <ul class="correction-dialog__changes" data-testid="correction-changes">
        @for (change of data.changes; track change.label) {
          <li data-testid="correction-change">
            <strong>{{ change.label | transloco }}:</strong>
            {{ change.from.key ? (change.from.key | transloco) : change.from.text }} →
            {{ change.to.key ? (change.to.key | transloco) : change.to.text }}
          </li>
        }
      </ul>
      <p class="correction-dialog__reason">
        {{ 'awards.correction.confirm.reason' | transloco: { reason: data.reason } }}
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="false" data-testid="confirm-cancel">
        {{ 'awards.correction.confirm.cancel' | transloco }}
      </button>
      <button mat-flat-button type="button" [mat-dialog-close]="true" data-testid="confirm-accept">
        {{ 'awards.correction.confirm.send' | transloco }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .correction-dialog__changes {
      margin: 0;
      padding-left: 20px;
      overflow-wrap: anywhere;
    }

    .correction-dialog__reason {
      overflow-wrap: anywhere;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CorrectionDialogComponent {
  protected readonly data = inject<CorrectionDialogData>(MAT_DIALOG_DATA);
}
