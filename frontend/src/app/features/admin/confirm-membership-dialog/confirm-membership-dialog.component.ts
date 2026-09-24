import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatOption, MatSelect } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../core/i18n/language.service';
import { OrganizationSummary } from '../../auth/registration.service';
import { organizationName } from '../role-organizations';
import { UserSummary, UsersService } from '../users.service';

@Component({
  selector: 'app-confirm-membership-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatFormField,
    MatLabel,
    MatSelect,
    MatOption,
    MatButton,
    TranslocoPipe,
  ],
  templateUrl: './confirm-membership-dialog.component.html',
  styles: '.admin__field { width: 100%; }',
})
export class ConfirmMembershipDialogComponent implements OnInit {
  private readonly api = inject(UsersService);
  private readonly language = inject(LanguageService);

  readonly user = inject<UserSummary>(MAT_DIALOG_DATA);
  readonly dialog = inject<MatDialogRef<ConfirmMembershipDialogComponent, number>>(MatDialogRef);
  readonly departments = signal<OrganizationSummary[]>([]);
  readonly department = new FormControl(this.user.organization.id, {
    nonNullable: true,
    validators: Validators.required,
  });

  ngOnInit(): void {
    this.api.organizations('DEPARTMENT').subscribe((departments) => this.departments.set(departments));
  }

  name(organization: { name: string; nameUk: string | null }): string {
    return organizationName(organization, this.language.current());
  }

  submit(): void {
    if (this.department.valid) {
      this.dialog.close(this.department.value);
    }
  }
}
