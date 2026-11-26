import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatError, MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { fieldProblems, knownProblem, problemType } from '../../../core/api/problem';
import { LanguageService } from '../../../core/i18n/language.service';
import { organizationName } from '../../../shared/organization-name';
import { UnitRef } from '../../awards/awards.service';
import { PeopleService, Person, PersonForm } from '../people.service';

const NAME_LENGTH = 100;

/** Problems the dialog explains in its own words; anything else shows the generic message. */
const KNOWN = ['email-taken', 'version-conflict', 'person-claimed', 'access-denied', 'network'];

/** What the dialog edits: nothing for a new person, the person for a change. */
export interface PersonDialogData {
  person: Person | null;
}

@Component({
  selector: 'app-person-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatDialogClose,
    MatFormField,
    MatLabel,
    MatHint,
    MatError,
    MatInput,
    MatSelect,
    MatOption,
    MatButton,
    RouterLink,
    TranslocoPipe,
  ],
  templateUrl: './person-dialog.component.html',
  styles: `
    .admin__form {
      display: flex;
      flex-direction: column;
      padding-top: 8px;
    }
    .admin__field {
      width: 100%;
    }
    .admin__problem {
      color: var(--mat-sys-error);
      margin: 0 0 var(--space-2, 8px);
    }
    .admin__matches {
      margin: 0 0 var(--space-2, 8px);
      padding-left: 20px;
    }
  `,
})
export class PersonDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(PeopleService);
  private readonly language = inject(LanguageService);
  private readonly ref = inject<MatDialogRef<PersonDialogComponent, Person>>(MatDialogRef);
  protected readonly data = inject<PersonDialogData>(MAT_DIALOG_DATA);

  readonly departments = signal<UnitRef[]>([]);
  readonly saving = signal(false);
  readonly problem = signal<string | null>(null);
  readonly matches = signal<Person[]>([]);

  readonly form = this.fb.nonNullable.group({
    firstName: [
      this.data.person?.firstName ?? '',
      [Validators.required, Validators.maxLength(NAME_LENGTH)],
    ],
    lastName: [
      this.data.person?.lastName ?? '',
      [Validators.required, Validators.maxLength(NAME_LENGTH)],
    ],
    organizationId: [
      this.data.person?.organization.id ?? (null as number | null),
      Validators.required,
    ],
    email: [
      this.data.person?.email ?? '',
      this.data.person?.email ? [Validators.required, Validators.email] : [Validators.email],
    ],
  });

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.matches.set([]));
    this.api.departments().subscribe({
      next: (units) => this.departments.set(units),
      error: () => this.problem.set('admin.people.problems.departments'),
    });
  }

  get editing(): boolean {
    return this.data.person !== null;
  }

  save(duplicateAcknowledged = false): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const email = value.email.trim();
    const request: PersonForm = {
      firstName: value.firstName.trim(),
      lastName: value.lastName.trim(),
      organizationId: value.organizationId as number,
      email: email === '' ? null : email,
    };
    if (duplicateAcknowledged) {
      request.duplicateAcknowledged = true;
    }
    this.saving.set(true);
    this.problem.set(null);
    const call = this.data.person
      ? this.api.change(this.data.person.id, { ...request, version: this.data.person.version })
      : this.api.add(request);
    call.subscribe({
      next: (person) => this.ref.close(person),
      error: (err: unknown) => this.refused(err),
    });
  }

  name(unit: UnitRef): string {
    return organizationName(unit, this.language.current());
  }

  private refused(err: unknown): void {
    this.saving.set(false);
    const type = problemType(err);
    if (type === 'possible-duplicate') {
      const body = (err as HttpErrorResponse).error as { matches?: Person[] } | null;
      this.matches.set(body?.matches ?? []);
      return;
    }
    this.matches.set([]);
    if (type === 'email-taken') {
      this.form.controls.email.setErrors({ taken: true });
      return;
    }
    const fields = fieldProblems(err);
    if (fields.length) {
      for (const field of fields) {
        this.form.get(field.field)?.setErrors({ [field.code]: true });
      }
      return;
    }
    this.problem.set(`admin.people.problems.${knownProblem(type, KNOWN)}`);
  }
}
