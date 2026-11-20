import { HttpStatusCode } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBar } from '@angular/material/progress-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Store } from '@ngrx/store';

import { problemStatus, problemType } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { canManagePeople, grantableRoles } from '../../../core/auth/permissions';
import { OrganizationRef, RoleAssignment } from '../../../core/auth/user-profile';
import { LanguageService } from '../../../core/i18n/language.service';
import { IconComponent } from '../../../shared/icons/icon.component';
import { AssignRoleDialogComponent } from '../assign-role-dialog/assign-role-dialog.component';
import { LegalHoldCardComponent } from '../legal-hold-card/legal-hold-card.component';
import { PersonCardComponent } from '../person-card/person-card.component';
import {
  ConfirmMembershipDialogComponent,
  MembershipChoice,
} from '../confirm-membership-dialog/confirm-membership-dialog.component';
import { PeopleService, placeholderAddress } from '../people.service';
import { RevokeRoleDialogComponent } from '../revoke-role-dialog/revoke-role-dialog.component';
import { organizationName } from '../../../shared/organization-name';
import { AdminUsersActions } from '../store/admin-users.actions';
import { adminUsersFeature } from '../store/admin-users.feature';
import { UserDetail, UsersService } from '../users.service';
import { kyivDateTime, kyivToday } from '../../../shared/date-format';
import { KyivDatePipe } from '../../../shared/kyiv-date.pipe';
import { StatusPageComponent } from '../../../shared/status-page/status-page.component';

/**
 * The person selected on the users page, in the pane beside the list: profile, roles, history and the actions on
 * the account. Follows the id in the address, so selecting another row reuses the pane.
 */
@Component({
  selector: 'app-user-detail',
  imports: [
    MatCard,
    MatCardHeader,
    MatCardTitle,
    MatCardContent,
    MatProgressBar,
    MatButton,
    IconComponent,
    RouterLink,
    TranslocoPipe,
    KyivDatePipe,
    LegalHoldCardComponent,
    PersonCardComponent,
    StatusPageComponent,
  ],
  templateUrl: './user-detail.component.html',
  styleUrl: './user-detail.component.scss',
})
export class UserDetailComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly api = inject(UsersService);
  private readonly people = inject(PeopleService);
  private readonly auth = inject(AuthService);
  private readonly language = inject(LanguageService);
  private readonly destroyRef = inject(DestroyRef);

  protected id = 0;
  protected readonly canAssign = grantableRoles(this.auth.permissions()).length > 0;
  protected readonly canManagePeople = canManagePeople(this.auth.permissions());
  protected readonly canManageHolds = this.auth.permissions().hasPermission('legal-hold:manage');

  readonly user = this.store.selectSignal(adminUsersFeature.selectSelected);
  readonly loading = this.store.selectSignal(adminUsersFeature.selectSelectedLoading);
  readonly problem = this.store.selectSignal(adminUsersFeature.selectSelectedProblem);
  readonly notFound = this.store.selectSignal(adminUsersFeature.selectNotFound);
  readonly message = signal<string | null>(null);
  readonly actionProblem = signal<string | null>(null);
  readonly linked = signal<number | null>(null);
  readonly confirmingId = this.store.selectSignal(adminUsersFeature.selectConfirmingId);
  readonly confirmProblem = this.store.selectSignal(adminUsersFeature.selectConfirmProblem);

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.id = Number(params.get('id'));
      this.message.set(null);
      this.actionProblem.set(null);
      this.linked.set(null);
      this.store.dispatch(AdminUsersActions.userOpened({ id: this.id }));
    });
  }

  reload(): void {
    this.changed();
  }

  /** Confirms that the person works in the unit the account names. */
  confirm(user: UserDetail): void {
    this.store.dispatch(
      AdminUsersActions.membershipConfirmed({ id: user.id, organizationId: user.organization.id }),
    );
  }

  /** Confirms the person in another unit, or links the account to a person without one. */
  correctDepartment(user: UserDetail): void {
    this.dialog
      .open(ConfirmMembershipDialogComponent, { data: user, width: '480px' })
      .afterClosed()
      .subscribe((choice?: MembershipChoice) => {
        if (choice?.person) {
          this.store.dispatch(
            AdminUsersActions.membershipLinked({
              id: user.id,
              personId: choice.person.id,
              version: choice.person.version,
            }),
          );
        } else if (choice) {
          this.store.dispatch(
            AdminUsersActions.membershipConfirmed({
              id: user.id,
              organizationId: choice.organizationId,
            }),
          );
        }
      });
  }

  confirmProblemFor(user: UserDetail): string | null {
    const problem = this.confirmProblem();
    return problem && problem.id === user.id ? `admin.problems.${problem.problem}` : null;
  }

  /** The address to show; a person whose address is unknown carries a placeholder that stays hidden. */
  address(user: UserDetail): string | null {
    return user.status === 'UNCLAIMED' && placeholderAddress(user.email) ? null : user.email;
  }

  assign(user: UserDetail): void {
    this.dialog
      .open(AssignRoleDialogComponent, { data: user, width: '520px' })
      .afterClosed()
      .subscribe((assignment?: RoleAssignment) => {
        if (assignment) {
          this.actionProblem.set(null);
          this.message.set('admin.detail.assigned');
          this.changed();
        }
      });
  }

  /** Offers the people without an account of a department whose awards this member takes over. */
  linkPerson(user: UserDetail): void {
    this.dialog
      .open(ConfirmMembershipDialogComponent, {
        data: { ...user, linkOnly: true },
        width: '480px',
      })
      .afterClosed()
      .subscribe((choice?: MembershipChoice) => {
        if (choice?.person) {
          this.people.link(choice.person.id, user.id, choice.person.version).subscribe({
            next: (result) => {
              if (this.movedOn(user.id)) {
                return;
              }
              this.actionProblem.set(null);
              this.message.set(null);
              this.linked.set(result.movedAwards);
              this.changed();
            },
            error: (err: unknown) => {
              if (this.movedOn(user.id)) {
                return;
              }
              this.linked.set(null);
              this.actionProblem.set(`admin.problems.${problemType(err)}`);
            },
          });
        }
      });
  }

  revoke(assignment: RoleAssignment, user: UserDetail): void {
    this.dialog
      .open(RevokeRoleDialogComponent, {
        data: { assignment, name: `${user.firstName} ${user.lastName}` },
        width: '480px',
      })
      .afterClosed()
      .subscribe((confirmed?: boolean) => {
        if (confirmed) {
          this.revokeConfirmed(assignment);
        }
      });
  }

  canRevoke(assignment: RoleAssignment): boolean {
    return this.auth.permissions().canGrant(assignment.role);
  }

  ended(assignment: RoleAssignment): boolean {
    return !!assignment.validTo && assignment.validTo < kyivToday();
  }

  moment(value: string): string {
    return kyivDateTime(value, this.language.current());
  }

  name(organization: OrganizationRef): string {
    return organizationName(organization, this.language.current());
  }

  /** Loads the person again and refreshes the list row beside it. */
  private changed(): void {
    this.store.dispatch(AdminUsersActions.userReloaded({ id: this.id }));
    this.store.dispatch(AdminUsersActions.refreshed());
  }

  /** Whether the pane shows another person than the one an answer is about; the list row still refreshes. */
  private movedOn(id: number): boolean {
    if (id === this.id) {
      return false;
    }
    this.store.dispatch(AdminUsersActions.refreshed());
    return true;
  }

  private revokeConfirmed(assignment: RoleAssignment): void {
    const id = this.id;
    this.api.revokeRole(id, assignment.id).subscribe({
      next: () => {
        if (this.movedOn(id)) {
          return;
        }
        this.actionProblem.set(null);
        this.message.set('admin.detail.revoked');
        this.changed();
      },
      error: (err: unknown) => {
        if (this.movedOn(id)) {
          return;
        }
        this.message.set(null);
        this.actionProblem.set(`admin.problems.${problemType(err)}`);
        if (problemStatus(err) === HttpStatusCode.Conflict) {
          this.store.dispatch(AdminUsersActions.userReloaded({ id }));
        }
      },
    });
  }
}
