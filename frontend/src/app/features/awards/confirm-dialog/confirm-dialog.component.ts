import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { Observable, map } from 'rxjs';

/** Translation keys of a yes-or-no question. */
export interface ConfirmDialogData {
  title: string;
  text: string;
  confirm: string;
  cancel: string;
  /** Values for the placeholders of the text. */
  params?: Record<string, string>;
}

@Component({
  selector: 'app-confirm-dialog',
  imports: [
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatDialogClose,
    MatButton,
    TranslocoPipe,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.title | transloco }}</h2>
    <mat-dialog-content>
      <p>{{ data.text | transloco: data.params }}</p>
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

/**
 * Asks a yes-or-no question whose translation keys are `title`, `text`, `confirm` and `cancel` under one prefix.
 *
 * @param dialog the dialog service
 * @param prefix the translation key prefix, e.g. `awards.remove`
 * @param params values for the placeholders of the text
 * @return true only when the user confirms
 */
export function confirmAction(
  dialog: MatDialog,
  prefix: string,
  params?: Record<string, string>,
): Observable<boolean> {
  const data: ConfirmDialogData = {
    title: `${prefix}.title`,
    text: `${prefix}.text`,
    confirm: `${prefix}.confirm`,
    cancel: `${prefix}.cancel`,
    params,
  };
  return dialog
    .open(ConfirmDialogComponent, { data, width: '420px' })
    .afterClosed()
    .pipe(map((confirmed?: boolean) => confirmed === true));
}
