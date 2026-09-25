import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatChip } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { TranslocoPipe } from '@jsverse/transloco';
import { Store } from '@ngrx/store';

import { AuthService } from '../../../core/auth/auth.service';
import { canDelegate } from '../../../core/auth/permissions';
import { OrganizationRef } from '../../../core/auth/user-profile';
import { LanguageService } from '../../../core/i18n/language.service';
import { organizationName } from '../../admin/role-organizations';
import { Delegation, UserBrief } from '../delegations.service';
import { DelegateDialogComponent } from '../delegate-dialog/delegate-dialog.component';
import { RevokeDelegationDialogComponent } from '../revoke-delegation-dialog/revoke-delegation-dialog.component';
import { DelegationsActions } from '../store/delegations.actions';
import { delegationsFeature } from '../store/delegations.feature';

@Component({
  selector: 'app-delegation-list',
  imports: [MatTableModule, MatChip, MatProgressBar, MatButton, TranslocoPipe],
  templateUrl: './delegation-list.component.html',
  styleUrl: './delegation-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DelegationListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly dialog = inject(MatDialog);
  private readonly auth = inject(AuthService);
  private readonly language = inject(LanguageService);

  protected readonly givenColumns = ['role', 'organization', 'person', 'period', 'state', 'actions'];
  protected readonly receivedColumns = ['role', 'organization', 'person', 'period', 'state'];
  protected readonly canDelegate = canDelegate(this.auth.permissions());

  readonly given = this.store.selectSignal(delegationsFeature.selectGiven);
  readonly received = this.store.selectSignal(delegationsFeature.selectReceived);
  readonly loading = this.store.selectSignal(delegationsFeature.selectLoading);
  readonly problem = this.store.selectSignal(delegationsFeature.selectProblem);
  readonly revokingId = this.store.selectSignal(delegationsFeature.selectRevokingId);
  readonly revokeProblem = this.store.selectSignal(delegationsFeature.selectRevokeProblem);
  readonly message = this.store.selectSignal(delegationsFeature.selectMessage);

  ngOnInit(): void {
    this.store.dispatch(DelegationsActions.opened());
  }

  delegate(): void {
    this.dialog
      .open(DelegateDialogComponent, { width: '560px' })
      .afterClosed()
      .subscribe((delegation?: Delegation) => {
        if (delegation) {
          this.store.dispatch(DelegationsActions.created({ delegation }));
        }
      });
  }

  revoke(delegation: Delegation): void {
    this.dialog
      .open(RevokeDelegationDialogComponent, { data: delegation, width: '480px' })
      .afterClosed()
      .subscribe((confirmed?: boolean) => {
        if (confirmed) {
          this.store.dispatch(DelegationsActions.revokeRequested({ id: delegation.id }));
        }
      });
  }

  problemFor(delegation: Delegation): string | null {
    const problem = this.revokeProblem();
    return problem && problem.id === delegation.id ? `delegations.problems.${problem.problem}` : null;
  }

  person(user: UserBrief): string {
    return `${user.lastName} ${user.firstName}`;
  }

  name(organization: OrganizationRef): string {
    return organizationName(organization, this.language.current());
  }
}
