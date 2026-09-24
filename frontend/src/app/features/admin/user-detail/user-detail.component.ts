import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatProgressBar } from '@angular/material/progress-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Store } from '@ngrx/store';

import { problemType } from '../../../core/api/problem';
import { AuthService } from '../../../core/auth/auth.service';
import { grantableRoles } from '../../../core/auth/permissions';
import { OrganizationRef, RoleAssignment } from '../../../core/auth/user-profile';
import { LanguageService } from '../../../core/i18n/language.service';
import { AssignRoleDialogComponent } from '../assign-role-dialog/assign-role-dialog.component';
import { RevokeRoleDialogComponent } from '../revoke-role-dialog/revoke-role-dialog.component';
import { organizationName, today } from '../role-organizations';
import { AdminUsersActions } from '../store/admin-users.actions';
import { adminUsersFeature } from '../store/admin-users.feature';
import { UserDetail, UsersService } from '../users.service';

@Component({
  selector: 'app-user-detail',
  imports: [
    MatCard,
    MatCardHeader,
    MatCardTitle,
    MatCardContent,
    MatProgressBar,
    MatButton,
    MatIcon,
    RouterLink,
    TranslocoPipe,
  ],
  templateUrl: './user-detail.component.html',
  styleUrl: './user-detail.component.scss',
})
export class UserDetailComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly api = inject(UsersService);
  private readonly auth = inject(AuthService);
  private readonly language = inject(LanguageService);

  protected readonly id = Number(this.route.snapshot.paramMap.get('id'));
  protected readonly canAssign = grantableRoles(this.auth.permissions()).length > 0;

  readonly user = this.store.selectSignal(adminUsersFeature.selectSelected);
  readonly loading = this.store.selectSignal(adminUsersFeature.selectSelectedLoading);
  readonly problem = this.store.selectSignal(adminUsersFeature.selectSelectedProblem);
  readonly notFound = this.store.selectSignal(adminUsersFeature.selectNotFound);
  readonly message = signal<string | null>(null);
  readonly actionProblem = signal<string | null>(null);

  ngOnInit(): void {
    this.store.dispatch(AdminUsersActions.userOpened({ id: this.id }));
  }

  assign(user: UserDetail): void {
    this.dialog
      .open(AssignRoleDialogComponent, { data: user, width: '520px' })
      .afterClosed()
      .subscribe((assignment?: RoleAssignment) => {
        if (assignment) {
          this.actionProblem.set(null);
          this.message.set('admin.detail.assigned');
          this.store.dispatch(AdminUsersActions.userReloaded({ id: this.id }));
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
    return !!assignment.validTo && assignment.validTo < today();
  }

  name(organization: OrganizationRef): string {
    return organizationName(organization, this.language.current());
  }

  private revokeConfirmed(assignment: RoleAssignment): void {
    this.api.revokeRole(this.id, assignment.id).subscribe({
      next: () => {
        this.actionProblem.set(null);
        this.message.set('admin.detail.revoked');
        this.store.dispatch(AdminUsersActions.userReloaded({ id: this.id }));
      },
      error: (err: unknown) => {
        this.message.set(null);
        this.actionProblem.set(`admin.problems.${problemType(err)}`);
      },
    });
  }
}
