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

/** Translation keys of a yes-or-no question. */
export interface ConfirmDialogData {
  title: string;
  text: string;
  confirm: string;
  cancel: string;
}

@Component({
  selector: 'app-confirm-dialog',
  imports: [MatDialogTitle, MatDialogContent, MatDialogActions, MatDialogClose, MatButton, TranslocoPipe],
  template: `
    <h2 mat-dialog-title>{{ data.title | transloco }}</h2>
    <mat-dialog-content>
      <p>{{ data.text | transloco }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="false" data-testid="confirm-cancel">
        {{ data.cancel | transloco }}
      </button>
      <button mat-flat-button type="button" [mat-dialog-close]="true" data-testid="confirm-accept">
        {{ data.confirm | transloco }}
      </button>
    </mat-dialog-actions>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmDialogComponent {
  protected readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
}
