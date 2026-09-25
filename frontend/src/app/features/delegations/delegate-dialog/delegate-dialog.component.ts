import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButton } from '@angular/material/button';
import {
  MatDialogActions,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatError, MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';
import { debounceTime, distinctUntilChanged, forkJoin } from 'rxjs';

import { problemType } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { delegatableOrganizations, delegatableRoles } from '../../../core/auth/permissions';
import { RoleType } from '../../../core/auth/user-profile';
import { LanguageService } from '../../../core/i18n/language.service';
import { organizationName, organizationTypesFor, today } from '../../admin/role-organizations';
import { UserSummary, UsersService } from '../../admin/users.service';
import { OrganizationSummary } from '../../auth/registration.service';
import { Delegation, DelegationsService } from '../delegations.service';

const DEBOUNCE = 300;
const MINIMUM_QUERY = 2;
const MAXIMUM_DAYS = 90;
const REASON_LIMIT = 500;

@Component({
  selector: 'app-delegate-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatFormField,
    MatLabel,
    MatHint,
    MatError,
    MatInput,
    MatSelect,
    MatOption,
    MatAutocompleteModule,
    MatButton,
    TranslocoPipe,
  ],
  templateUrl: './delegate-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .delegations__form { display: flex; flex-direction: column; }
    .delegations__field { width: 100%; }
    .delegations__problem { color: var(--mat-sys-error, #b3261e); margin: 0; }
  `,
})
export class DelegateDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly users = inject(UsersService);
  private readonly api = inject(DelegationsService);
  private readonly auth = inject(AuthService);
  private readonly language = inject(LanguageService);

  protected readonly dialog =
    inject<MatDialogRef<DelegateDialogComponent, Delegation>>(MatDialogRef);
  readonly roles = delegatableRoles(this.auth.permissions());
  readonly organizations = signal<OrganizationSummary[]>([]);
  readonly candidates = signal<UserSummary[]>([]);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly minimumDate = today();
  readonly reasonLimit = REASON_LIMIT;

  readonly form = this.fb.nonNullable.group({
    role: [null as RoleType | null, Validators.required],
    organizationId: [null as number | null, Validators.required],
    delegate: [null as UserSummary | string | null, Validators.required],
    validFrom: [today(), Validators.required],
    validTo: ['', Validators.required],
    reason: ['', Validators.maxLength(REASON_LIMIT)],
  });

  constructor() {
    this.form.controls.delegate.valueChanges
      .pipe(debounceTime(DEBOUNCE), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((value) => this.search(value));
  }

  roleChanged(role: RoleType): void {
    this.form.controls.organizationId.reset(null);
    this.organizations.set([]);
    const allowed = delegatableOrganizations(this.auth.permissions(), role);
    forkJoin(organizationTypesFor(role).map((type) => this.users.organizations(type))).subscribe(
      (lists) => {
        const mine = lists.flat().filter((organization) => allowed.includes(organization.id));
        this.organizations.set(mine);
        if (mine.length === 1) {
          this.form.controls.organizationId.setValue(mine[0].id);
        }
      },
    );
  }

  name(organization: { name: string; nameUk: string | null }): string {
    return organizationName(organization, this.language.current());
  }

  display(user: UserSummary | string | null): string {
    return user && typeof user !== 'string' ? `${user.lastName} ${user.firstName} (${user.email})` : '';
  }

  submit(): void {
    const value = this.form.getRawValue();
    const delegate = typeof value.delegate === 'string' ? null : value.delegate;
    if (this.form.invalid || this.submitting() || !value.role || !value.organizationId || !delegate) {
      this.form.markAllAsTouched();
      return;
    }
    if (!this.datesValid(value.validFrom, value.validTo)) {
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.api
      .create({
        delegateId: delegate.id,
        role: value.role,
        organizationId: value.organizationId,
        validFrom: value.validFrom,
        validTo: value.validTo,
        reason: value.reason.trim() || null,
      })
      .subscribe({
        next: (delegation) => this.dialog.close(delegation),
        error: (err: unknown) => {
          this.error.set(`delegations.problems.${problemType(err)}`);
          this.submitting.set(false);
        },
      });
  }

  private datesValid(validFrom: string, validTo: string): boolean {
    if (validFrom < this.minimumDate) {
      this.form.controls.validFrom.setErrors({ past: true });
      return false;
    }
    if (validTo < validFrom) {
      this.form.controls.validTo.setErrors({ order: true });
      return false;
    }
    if (validTo > this.latestEnd(validFrom)) {
      this.form.controls.validTo.setErrors({ range: true });
      return false;
    }
    return true;
  }

  private latestEnd(validFrom: string): string {
    const end = new Date(`${validFrom}T00:00:00Z`);
    end.setUTCDate(end.getUTCDate() + MAXIMUM_DAYS);
    return end.toISOString().substring(0, 10);
  }

  private search(value: UserSummary | string | null): void {
    const query = typeof value === 'string' ? value.trim() : '';
    if (query.length < MINIMUM_QUERY) {
      this.candidates.set([]);
      return;
    }
    this.users
      .list({ q: query, role: null, status: 'ACTIVE', unconfirmed: false, page: 0, size: 10 })
      .subscribe((page) => this.candidates.set(page.content));
  }
}
