import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatButton } from '@angular/material/button';
import {
  MatDialog,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { Observable, map } from 'rxjs';

/** What a public award shows to everybody. */
const PUBLISHED = [
  'name',
  'department',
  'title',
  'description',
  'category',
  'awardingOrganization',
  'date',
  'link',
  'verified',
];
/** What stays with the owner and the reviewers. */
const KEPT = ['email', 'documents', 'reviewers', 'comments'];

@Component({
  selector: 'app-publish-dialog',
  imports: [
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatDialogClose,
    MatButton,
    TranslocoPipe,
  ],
  template: `
    <h2 mat-dialog-title>{{ 'awards.visibility.publish.title' | transloco }}</h2>
    <mat-dialog-content>
      <p>{{ 'awards.visibility.publish.text' | transloco }}</p>
      <h3 class="publish__heading">{{ 'awards.visibility.publish.shown' | transloco }}</h3>
      <ul data-testid="publish-shown">
        @for (item of published; track item) {
          <li>{{ 'awards.visibility.publish.items.' + item | transloco }}</li>
        }
      </ul>
      <h3 class="publish__heading">{{ 'awards.visibility.publish.kept' | transloco }}</h3>
      <ul data-testid="publish-kept">
        @for (item of kept; track item) {
          <li>{{ 'awards.visibility.publish.items.' + item | transloco }}</li>
        }
      </ul>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="false" data-testid="confirm-cancel">
        {{ 'awards.visibility.publish.cancel' | transloco }}
      </button>
      <button mat-flat-button type="button" [mat-dialog-close]="true" data-testid="confirm-accept">
        {{ 'awards.visibility.publish.confirm' | transloco }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .publish__heading {
      margin: 16px 0 4px;
      font: var(--mat-sys-title-small);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublishDialogComponent {
  protected readonly published = PUBLISHED;
  protected readonly kept = KEPT;
}

/**
 * Lists what a public award shows and what it keeps back, and asks to publish it.
 *
 * @param dialog the dialog service
 * @return true only when the owner confirms
 */
export function confirmPublication(dialog: MatDialog): Observable<boolean> {
  return dialog
    .open(PublishDialogComponent, { width: '480px' })
    .afterClosed()
    .pipe(map((confirmed?: boolean) => confirmed === true));
}
