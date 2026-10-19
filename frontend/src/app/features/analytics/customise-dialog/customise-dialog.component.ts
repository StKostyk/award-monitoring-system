import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';

import { DASHBOARD_WIDGETS, DashboardWidget } from '../analytics.service';

/** Ticks the widgets to show; closes with the list of widgets to hide. */
@Component({
  selector: 'app-customise-dialog',
  imports: [
    MatButton,
    MatCheckbox,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ 'analytics.customise.title' | transloco }}</h2>
    <mat-dialog-content>
      <p>{{ 'analytics.customise.hint' | transloco }}</p>
      @for (widget of widgets; track widget) {
        <mat-checkbox
          class="customise__option"
          [checked]="!hidden().has(widget)"
          (change)="toggle(widget, $event.checked)"
          [attr.data-testid]="'customise-' + widget"
        >
          {{ 'analytics.widgets.' + widget | transloco }}
        </mat-checkbox>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>
        {{ 'analytics.customise.cancel' | transloco }}
      </button>
      <button mat-flat-button type="button" (click)="save()" data-testid="customise-save">
        {{ 'analytics.customise.save' | transloco }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .customise__option {
      display: block;
    }
  `,
})
export class CustomiseDialogComponent {
  private readonly ref = inject(MatDialogRef<CustomiseDialogComponent, DashboardWidget[]>);

  protected readonly widgets = DASHBOARD_WIDGETS;
  protected readonly hidden = signal(
    new Set<DashboardWidget>(inject<DashboardWidget[]>(MAT_DIALOG_DATA)),
  );

  protected toggle(widget: DashboardWidget, shown: boolean): void {
    const hidden = new Set(this.hidden());
    if (shown) {
      hidden.delete(widget);
    } else {
      hidden.add(widget);
    }
    this.hidden.set(hidden);
  }

  protected save(): void {
    this.ref.close(DASHBOARD_WIDGETS.filter((widget) => this.hidden().has(widget)));
  }
}
