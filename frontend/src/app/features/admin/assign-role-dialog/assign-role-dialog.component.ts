import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';
import { forkJoin } from 'rxjs';

import { problemType } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { grantableRoles } from '../../../core/auth/permissions';
import { RoleAssignment, RoleType } from '../../../core/auth/user-profile';
import { LanguageService } from '../../../core/i18n/language.service';
import { OrganizationSummary } from '../../auth/registration.service';
import { organizationName, organizationTypesFor, today } from '../role-organizations';
import { UserSummary, UsersService } from '../users.service';

@Component({
  selector: 'app-assign-role-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatFormField,
    MatLabel,
    MatError,
    MatInput,
    MatSelect,
    MatOption,
    MatButton,
    TranslocoPipe,
  ],
  templateUrl: './assign-role-dialog.component.html',
  styles: `
    .admin__form { display: flex; flex-direction: column; }
    .admin__field { width: 100%; }
    .admin__problem { color: var(--mat-sys-error, #b3261e); margin: 0; }
  `,
})
export class AssignRoleDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(UsersService);
  private readonly auth = inject(AuthService);
  private readonly language = inject(LanguageService);

  protected readonly user = inject<UserSummary>(MAT_DIALOG_DATA);
  protected readonly dialog =
    inject<MatDialogRef<AssignRoleDialogComponent, RoleAssignment>>(MatDialogRef);
  protected readonly roles = grantableRoles(this.auth.permissions());
  protected readonly organizations = signal<OrganizationSummary[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly minimumDate = today();

  readonly form = this.fb.nonNullable.group({
    role: [null as RoleType | null, Validators.required],
    organizationId: [null as number | null, Validators.required],
    validFrom: [today(), Validators.required],
    validTo: [''],
  });

  roleChanged(role: RoleType): void {
    this.form.controls.organizationId.reset(null);
    this.organizations.set([]);
    forkJoin(organizationTypesFor(role).map((type) => this.api.organizations(type))).subscribe(
      (lists) => this.organizations.set(lists.flat()),
    );
  }

  name(organization: { name: string; nameUk: string | null }): string {
    return organizationName(organization, this.language.current());
  }

  submit(): void {
    const value = this.form.getRawValue();
    if (this.form.invalid || this.submitting() || !value.role || !value.organizationId) {
      this.form.markAllAsTouched();
      return;
    }
    if (value.validFrom < this.minimumDate) {
      this.form.controls.validFrom.setErrors({ past: true });
      return;
    }
    if (value.validTo && value.validTo < value.validFrom) {
      this.form.controls.validTo.setErrors({ order: true });
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.api
      .assignRole(this.user.id, {
        role: value.role,
        organizationId: value.organizationId,
        validFrom: value.validFrom,
        validTo: value.validTo || null,
      })
      .subscribe({
        next: (assignment) => this.dialog.close(assignment),
        error: (err: unknown) => {
          this.error.set(`admin.problems.${problemType(err)}`);
          this.submitting.set(false);
        },
      });
  }
}
