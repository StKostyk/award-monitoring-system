import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../core/i18n/language.service';
import { DuplicateMatch, awardTitle } from '../awards.service';

/** The own awards a submission looks like. */
export interface DuplicateDialogData {
  matches: DuplicateMatch[];
}

@Component({
  selector: 'app-duplicate-dialog',
  imports: [
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatDialogClose,
    MatButton,
    RouterLink,
    DatePipe,
    TranslocoPipe,
  ],
  template: `
    <h2 mat-dialog-title>{{ 'awards.duplicate.title' | transloco }}</h2>
    <mat-dialog-content>
      <p>{{ 'awards.duplicate.text' | transloco }}</p>
      <ul class="duplicate-list">
        @for (match of data.matches; track match.id) {
          <li>
            <a
              [routerLink]="['/awards', match.id]"
              [mat-dialog-close]="false"
              [attr.data-testid]="'duplicate-match-' + match.id"
              >{{ title(match) }}</a
            >
            · {{ match.awardDate | date: 'dd.MM.yyyy' }} ·
            {{ 'awards.status.' + match.status | transloco }}
          </li>
        }
      </ul>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="false" data-testid="duplicate-cancel">
        {{ 'awards.duplicate.cancel' | transloco }}
      </button>
      <button
        mat-flat-button
        type="button"
        [mat-dialog-close]="true"
        data-testid="duplicate-confirm"
      >
        {{ 'awards.duplicate.confirm' | transloco }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .duplicate-list {
      padding-left: 1.25rem;
      overflow-wrap: anywhere;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DuplicateDialogComponent {
  protected readonly data = inject<DuplicateDialogData>(MAT_DIALOG_DATA);
  private readonly language = inject(LanguageService);

  protected title(match: DuplicateMatch): string {
    return awardTitle(match, this.language.current());
  }
}
