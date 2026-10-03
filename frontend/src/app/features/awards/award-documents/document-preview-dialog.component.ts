import { ChangeDetectionStrategy, Component, OnDestroy, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';

/** The image to show: its name and an object URL the dialog releases when it closes. */
export interface DocumentPreviewData {
  name: string;
  url: string;
}

@Component({
  selector: 'app-document-preview-dialog',
  imports: [
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatDialogClose,
    MatButton,
    TranslocoPipe,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.name }}</h2>
    <mat-dialog-content>
      <img
        class="document-preview"
        [src]="data.url"
        [alt]="data.name"
        data-testid="document-preview-image"
      />
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close data-testid="document-preview-close">
        {{ 'awards.documents.close' | transloco }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .document-preview {
      display: block;
      max-width: 100%;
      max-height: 70vh;
      margin: 0 auto;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DocumentPreviewDialogComponent implements OnDestroy {
  protected readonly data = inject<DocumentPreviewData>(MAT_DIALOG_DATA);

  ngOnDestroy(): void {
    URL.revokeObjectURL(this.data.url);
  }
}
