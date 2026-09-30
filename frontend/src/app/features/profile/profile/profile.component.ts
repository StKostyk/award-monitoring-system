import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { TranslocoPipe } from '@jsverse/transloco';

import { fieldProblems, problemType } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { OrganizationRef, UserProfile } from '../../../core/auth/user-profile';
import { LanguageService } from '../../../core/i18n/language.service';
import { organizationName } from '../../admin/role-organizations';
import { Delegation, DelegationsService } from '../../delegations/delegations.service';
import { EmailChangeDialogComponent } from '../email-change-dialog/email-change-dialog.component';
import { nameValidator } from '../name-rules';
import { NameChange, ProfileService } from '../profile.service';

type NameField = 'firstName' | 'lastName';

@Component({
  selector: 'app-profile',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatCard,
    MatCardHeader,
    MatCardTitle,
    MatCardContent,
    MatFormField,
    MatLabel,
    MatError,
    MatInput,
    MatButton,
    TranslocoPipe,
  ],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
})
export class ProfileComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly api = inject(ProfileService);
  private readonly delegations = inject(DelegationsService);
  private readonly language = inject(LanguageService);
  private readonly dialog = inject(MatDialog);

  readonly profile = signal<UserProfile | null>(null);
  readonly received = signal<Delegation[]>([]);
  readonly loadFailed = signal(false);
  readonly saving = signal(false);
  readonly message = signal<string | null>(null);
  readonly problem = signal<string | null>(null);
  readonly sentTo = signal<string | null>(null);

  readonly names = this.fb.nonNullable.group({
    firstName: ['', nameValidator],
    lastName: ['', nameValidator],
  });

  constructor() {
    this.auth
      .loadProfile()
      .then((profile) => this.show(profile))
      .catch(() => this.loadFailed.set(true));
    this.delegations.list('active').subscribe({
      next: (list) => this.received.set(list.received),
      error: () => this.received.set([]),
    });
  }

  name(organization: OrganizationRef): string {
    return organizationName(organization, this.language.current());
  }

  /** The code of the first error of a name field, as the API would report it. */
  firstError(field: NameField): string | null {
    const errors = this.names.controls[field].errors;
    return errors ? Object.keys(errors)[0] : null;
  }

  save(): void {
    const current = this.profile();
    if (!current || this.names.invalid || this.names.pristine || this.saving()) {
      return;
    }
    const change: NameChange = {};
    const { firstName, lastName } = this.names.getRawValue();
    if (firstName.trim() !== current.firstName) {
      change.firstName = firstName.trim();
    }
    if (lastName.trim() !== current.lastName) {
      change.lastName = lastName.trim();
    }
    this.saving.set(true);
    this.message.set(null);
    this.problem.set(null);
    this.api.updateNames(change).subscribe({
      next: (profile) => {
        this.auth.profile.set(profile);
        this.show(profile);
        this.message.set('profile.saved');
        this.saving.set(false);
      },
      error: (err: unknown) => {
        this.showProblem(err);
        this.saving.set(false);
      },
    });
  }

  changeAddress(): void {
    const current = this.profile();
    if (!current) {
      return;
    }
    this.dialog
      .open<EmailChangeDialogComponent, { email: string }, string>(EmailChangeDialogComponent, {
        data: { email: current.email },
        width: '480px',
      })
      .afterClosed()
      .subscribe((newEmail) => {
        if (newEmail) {
          this.sentTo.set(newEmail);
        }
      });
  }

  private show(profile: UserProfile): void {
    this.profile.set(profile);
    this.names.reset({ firstName: profile.firstName, lastName: profile.lastName });
  }

  private showProblem(err: unknown): void {
    const fields = fieldProblems(err);
    fields
      .filter((field) => field.field === 'firstName' || field.field === 'lastName')
      .forEach((field) =>
        this.names.controls[field.field as NameField].setErrors({ [field.code]: true }),
      );
    if (fields.length === 0) {
      const type = problemType(err);
      this.problem.set(type === 'network' ? 'profile.errors.network' : 'profile.errors.failed');
    }
  }
}
