import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatOption, MatSelect } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../../core/i18n/language.service';
import { organizationName } from '../../../../shared/organization-name';
import { AnalysableUnit } from '../unit-analytics.service';

/** The faculties and departments open to the caller, departments indented under their faculty. */
@Component({
  selector: 'app-unit-picker',
  imports: [MatFormField, MatLabel, MatOption, MatSelect, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-form-field class="picker" subscriptSizing="dynamic">
      <mat-label>{{ 'unitAnalytics.unit' | transloco }}</mat-label>
      <mat-select
        [value]="selected()"
        (selectionChange)="choose.emit($event.value)"
        data-testid="unit-picker"
      >
        @for (option of options(); track option.id) {
          <mat-option
            [value]="option.id"
            [class.picker__child]="option.nested"
            [attr.data-testid]="'unit-option-' + option.id"
            >{{ option.name }}</mat-option
          >
        }
      </mat-select>
    </mat-form-field>
  `,
  styles: `
    .picker {
      width: min(100%, 420px);
    }

    .picker__child {
      padding-left: 40px;
    }
  `,
})
export class UnitPickerComponent {
  private readonly language = inject(LanguageService);

  readonly units = input.required<AnalysableUnit[]>();
  readonly selected = input<number | null>(null);
  readonly choose = output<number>();

  protected readonly options = computed(() => {
    const language = this.language.current();
    const ids = new Set(this.units().map((unit) => unit.id));
    return this.units().map((unit) => ({
      id: unit.id,
      name: organizationName(unit, language),
      nested: unit.parentId !== null && ids.has(unit.parentId),
    }));
  });
}
