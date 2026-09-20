import { Component, OnInit, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatOptgroup, MatOption, MatSelect } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { problemType } from '../../../core/api/problem';
import { LanguageService } from '../../../core/i18n/language.service';
import { OrganizationSummary, RegistrationService } from '../registration.service';

export const PASSWORD_MIN = 10;
export const PASSWORD_MAX_BYTES = 72;
export const INSTITUTIONAL_DOMAIN = 'chnu.edu.ua';
export const INSTITUTIONAL_EMAIL = new RegExp(`^[^@\\s]+@${INSTITUTIONAL_DOMAIN.replace(/\./g, '\\.')}$`, 'i');
export const NAME = /^\p{L}[\p{L}'’\- ]*$/u;

/** Rejects passwords longer than the 72 bytes BCrypt can hash. */
export function maxUtf8Bytes(max: number): ValidatorFn {
  return (control: AbstractControl<string>) =>
    new TextEncoder().encode(control.value ?? '').length > max ? { maxbytes: { max } } : null;
}

export interface DepartmentGroup {
  faculty: string;
  departments: OrganizationSummary[];
}

@Component({
  selector: 'app-register',
  imports: [
    ReactiveFormsModule,
    MatCard,
    MatCardHeader,
    MatCardTitle,
    MatCardContent,
    MatFormField,
    MatLabel,
    MatError,
    MatInput,
    MatSelect,
    MatOptgroup,
    MatOption,
    MatButton,
    RouterLink,
    TranslocoPipe,
  ],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
})
export class RegisterComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(RegistrationService);
  private readonly router = inject(Router);
  private readonly language = inject(LanguageService);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.pattern(INSTITUTIONAL_EMAIL)]],
    password: ['', [Validators.required, Validators.minLength(PASSWORD_MIN), maxUtf8Bytes(PASSWORD_MAX_BYTES)]],
    firstName: ['', [Validators.required, Validators.maxLength(100), Validators.pattern(NAME)]],
    lastName: ['', [Validators.required, Validators.maxLength(100), Validators.pattern(NAME)]],
    organizationId: [null as number | null, Validators.required],
  });

  readonly groups = signal<DepartmentGroup[]>([]);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);

  ngOnInit(): void {
    this.api.departments().subscribe((departments) => this.groups.set(this.groupByFaculty(departments)));
  }

  name(organization: { name: string; nameUk: string | null }): string {
    return this.language.current() === 'uk' && organization.nameUk ? organization.nameUk : organization.name;
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();
    this.api
      .register({ ...value, email: value.email.trim(), organizationId: value.organizationId as number })
      .subscribe({
        next: (response) =>
          void this.router.navigate(['/registration-pending'], { queryParams: { email: response.email } }),
        error: (err) => {
          this.error.set(`register.errors.${problemType(err)}`);
          this.submitting.set(false);
        },
      });
  }

  groupByFaculty(departments: OrganizationSummary[]): DepartmentGroup[] {
    const groups = new Map<string, DepartmentGroup>();
    for (const department of departments) {
      const faculty = department.parent ? this.name(department.parent) : '';
      const group = groups.get(faculty) ?? { faculty, departments: [] };
      group.departments.push(department);
      groups.set(faculty, group);
    }
    return [...groups.values()].sort((a, b) => a.faculty.localeCompare(b.faculty));
  }
}
