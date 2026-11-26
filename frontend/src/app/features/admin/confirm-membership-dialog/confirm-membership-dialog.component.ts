import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
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
import { MatRadioButton, MatRadioGroup } from '@angular/material/radio';
import { MatOption, MatSelect } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, map, of, startWith, switchMap } from 'rxjs';

import { OrganizationRef } from '../../../core/auth/user-profile';
import { LanguageService } from '../../../core/i18n/language.service';
import { OrganizationSummary } from '../../../core/organizations/organizations.service';
import { organizationName } from '../../../shared/organization-name';
import { PeopleService, Person } from '../people.service';
import { UsersService } from '../users.service';

/** The account whose membership is confirmed, or which takes over a colleague's awards. */
export interface MembershipDialogData {
  id: number;
  firstName: string;
  lastName: string;
  organization: OrganizationRef;
  /** True for an account that already holds a role: only the link is offered, no new role. */
  linkOnly?: boolean;
}

/** The department chosen and, when the registrant is a person without an account, that person. */
export interface MembershipChoice {
  organizationId: number;
  person: Person | null;
}

/** People without an account listed for one department. */
const CANDIDATES = 100;

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
    MatRadioGroup,
    MatRadioButton,
    MatButton,
    TranslocoPipe,
  ],
  templateUrl: './confirm-membership-dialog.component.html',
  styles: `
    .admin__field {
      width: 100%;
    }
    .membership__people {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .membership__heading {
      margin: 8px 0 4px;
      font-weight: 500;
    }
  `,
})
export class ConfirmMembershipDialogComponent implements OnInit {
  private readonly api = inject(UsersService);
  private readonly people = inject(PeopleService);
  private readonly language = inject(LanguageService);
  private readonly destroyRef = inject(DestroyRef);

  readonly user = inject<MembershipDialogData>(MAT_DIALOG_DATA);
  readonly dialog =
    inject<MatDialogRef<ConfirmMembershipDialogComponent, MembershipChoice>>(MatDialogRef);
  readonly departments = signal<OrganizationSummary[]>([]);
  readonly candidates = signal<Person[]>([]);
  readonly department = new FormControl(this.user.organization.id, {
    nonNullable: true,
    validators: Validators.required,
  });
  readonly person = new FormControl<Person | null>(null);

  ngOnInit(): void {
    this.api
      .organizations('DEPARTMENT')
      .subscribe((departments) => this.departments.set(departments));
    this.department.valueChanges
      .pipe(
        startWith(this.department.value),
        switchMap((id) =>
          this.people.list('', CANDIDATES, id).pipe(
            map((page) => this.ordered(page.content)),
            catchError(() => of([] as Person[])),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((people) => {
        this.person.setValue(null);
        this.candidates.set(people);
      });
  }

  name(organization: { name: string; nameUk: string | null }): string {
    return organizationName(organization, this.language.current());
  }

  submittable(): boolean {
    return this.department.valid && (!this.user.linkOnly || this.person.value !== null);
  }

  submit(): void {
    if (this.submittable()) {
      this.dialog.close({ organizationId: this.department.value, person: this.person.value });
    }
  }

  private ordered(people: Person[]): Person[] {
    const lastName = this.user.lastName.trim().toLocaleLowerCase();
    const unclaimed = people.filter((person) => person.status === 'UNCLAIMED');
    return [
      ...unclaimed.filter((person) => person.lastName.toLocaleLowerCase() === lastName),
      ...unclaimed.filter((person) => person.lastName.toLocaleLowerCase() !== lastName),
    ];
  }
}
