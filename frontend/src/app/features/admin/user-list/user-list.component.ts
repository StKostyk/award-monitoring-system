import { Component, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatCard, MatCardContent } from '@angular/material/card';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatChip, MatChipSet } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MatPaginator, MatPaginatorIntl, PageEvent } from '@angular/material/paginator';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatOption, MatSelect } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltip } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Store } from '@ngrx/store';
import { debounceTime, distinctUntilChanged, map } from 'rxjs';

import { AccountStatus, OrganizationRef, RoleType } from '../../../core/auth/user-profile';
import { LanguageService } from '../../../core/i18n/language.service';
import { AdminPaginatorIntl } from '../admin-paginator-intl';
import { ConfirmMembershipDialogComponent } from '../confirm-membership-dialog/confirm-membership-dialog.component';
import { ROLES, STATUSES, organizationName } from '../role-organizations';
import { AdminUsersActions } from '../store/admin-users.actions';
import { adminUsersFeature } from '../store/admin-users.feature';
import { UserFilters, UserSummary } from '../users.service';

const DEBOUNCE = 300;
const MINIMUM_QUERY = 2;

@Component({
  selector: 'app-user-list',
  imports: [
    ReactiveFormsModule,
    MatCard,
    MatCardContent,
    MatFormField,
    MatLabel,
    MatHint,
    MatInput,
    MatSelect,
    MatOption,
    MatCheckbox,
    MatTableModule,
    MatPaginator,
    MatProgressBar,
    MatChipSet,
    MatChip,
    MatButton,
    MatIconButton,
    MatIcon,
    MatTooltip,
    RouterLink,
    TranslocoPipe,
  ],
  providers: [{ provide: MatPaginatorIntl, useClass: AdminPaginatorIntl }],
  templateUrl: './user-list.component.html',
  styleUrl: './user-list.component.scss',
})
export class UserListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly fb = inject(FormBuilder);
  private readonly dialog = inject(MatDialog);
  private readonly language = inject(LanguageService);

  protected readonly columns = ['name', 'email', 'organization', 'status', 'roles', 'actions'];
  protected readonly roles = ROLES;
  protected readonly statuses = STATUSES;
  protected readonly pageSizes = [20, 50, 100];

  readonly users = this.store.selectSignal(adminUsersFeature.selectUsers);
  readonly total = this.store.selectSignal(adminUsersFeature.selectTotal);
  readonly page = this.store.selectSignal(adminUsersFeature.selectPage);
  readonly size = this.store.selectSignal(adminUsersFeature.selectSize);
  readonly loading = this.store.selectSignal(adminUsersFeature.selectLoading);
  readonly problem = this.store.selectSignal(adminUsersFeature.selectProblem);
  readonly confirmingId = this.store.selectSignal(adminUsersFeature.selectConfirmingId);
  readonly confirmProblem = this.store.selectSignal(adminUsersFeature.selectConfirmProblem);

  readonly filters = this.fb.nonNullable.group({
    q: '',
    role: [null as RoleType | null],
    status: [null as AccountStatus | null],
    unconfirmed: false,
  });

  constructor() {
    this.filters.valueChanges
      .pipe(
        debounceTime(DEBOUNCE),
        map(() => this.query()),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        takeUntilDestroyed(),
      )
      .subscribe((filters) => this.store.dispatch(AdminUsersActions.filtersChanged({ filters })));
  }

  ngOnInit(): void {
    this.store.dispatch(AdminUsersActions.opened());
  }

  /** The filters as the API takes them; a free text under two characters is dropped. */
  query(): UserFilters {
    const value = this.filters.getRawValue();
    const q = value.q.trim();
    return {
      q: q.length < MINIMUM_QUERY ? '' : q,
      role: value.role,
      status: value.status,
      unconfirmed: value.unconfirmed,
    };
  }

  changePage(event: PageEvent): void {
    this.store.dispatch(
      AdminUsersActions.pageChanged({ page: event.pageIndex, size: event.pageSize }),
    );
  }

  confirm(user: UserSummary): void {
    this.store.dispatch(
      AdminUsersActions.membershipConfirmed({ id: user.id, organizationId: user.organization.id }),
    );
  }

  correctDepartment(user: UserSummary): void {
    this.dialog
      .open(ConfirmMembershipDialogComponent, { data: user, width: '480px' })
      .afterClosed()
      .subscribe((organizationId?: number) => {
        if (organizationId) {
          this.store.dispatch(AdminUsersActions.membershipConfirmed({ id: user.id, organizationId }));
        }
      });
  }

  problemFor(user: UserSummary): string | null {
    const problem = this.confirmProblem();
    return problem && problem.id === user.id ? `admin.problems.${problem.problem}` : null;
  }

  name(organization: OrganizationRef): string {
    return organizationName(organization, this.language.current());
  }
}
