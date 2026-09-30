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

import { AwardVersion, SNAPSHOT_FIELDS, SnapshotField } from '../awards.service';
import { FIELD_LABELS, ShownValue, ValueNames, shownValue } from './version-values';

/** The version to show and the names its ids are shown with. */
export interface VersionDialogData {
  version: AwardVersion;
  names: ValueNames;
}

@Component({
  selector: 'app-award-version-dialog',
  imports: [
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatDialogClose,
    MatButton,
    TranslocoPipe,
  ],
  template: `
    <h2 mat-dialog-title data-testid="version-dialog-title">
      {{ 'awards.history.version' | transloco: { number: data.version.number } }} ·
      {{ 'awards.history.actions.' + data.version.action | transloco }}
    </h2>
    <mat-dialog-content>
      <dl class="version-dialog__fields">
        @for (field of fields; track field) {
          @let shown = value(field);
          <dt>{{ labels[field] | transloco }}</dt>
          <dd [attr.data-testid]="'version-field-' + field">
            {{ shown.key ? (shown.key | transloco) : shown.text }}
          </dd>
        }
      </dl>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close data-testid="version-dialog-close">
        {{ 'awards.history.close' | transloco }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .version-dialog__fields {
      display: grid;
      grid-template-columns: minmax(120px, max-content) 1fr;
      gap: 8px 16px;
      margin: 0;
    }

    .version-dialog__fields dt {
      color: var(--mat-sys-on-surface-variant, #444746);
    }

    .version-dialog__fields dd {
      margin: 0;
      overflow-wrap: anywhere;
      white-space: pre-line;
    }

    @media (max-width: 480px) {
      .version-dialog__fields {
        grid-template-columns: 1fr;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AwardVersionDialogComponent {
  protected readonly data = inject<VersionDialogData>(MAT_DIALOG_DATA);
  protected readonly fields = SNAPSHOT_FIELDS;
  protected readonly labels = FIELD_LABELS;

  protected value(field: SnapshotField): ShownValue {
    return shownValue(field, this.data.version.snapshot[field], this.data.names);
  }
}
