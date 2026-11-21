import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  effect,
  inject,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatChip } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Store } from '@ngrx/store';
import { map } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { canDelegate } from '../../../core/auth/permissions';
import { OrganizationRef } from '../../../core/auth/user-profile';
import { LanguageService } from '../../../core/i18n/language.service';
import { kyivToday } from '../../../shared/date-format';
import { IconComponent } from '../../../shared/icons/icon.component';
import { KyivDatePipe } from '../../../shared/kyiv-date.pipe';
import { ListRowComponent, ListRowsDirective } from '../../../shared/layout/list-row.component';
import { PageHeaderComponent } from '../../../shared/layout/page-header.component';
import { PaneComponent } from '../../../shared/layout/pane.component';
import { SplitComponent } from '../../../shared/layout/split.component';
import { organizationName } from '../../../shared/organization-name';
import { pluralForm } from '../../../shared/plural';
import { SkeletonRowsComponent } from '../../../shared/skeleton/skeleton-rows.component';
import { initials } from '../../../shared/widgets/top-widget.component';
import { capabilitiesOf } from '../delegation-capabilities';
import { daysLeft } from '../delegation-days';
import { Delegation, UserBrief } from '../delegations.service';
import { DelegateDialogComponent } from '../delegate-dialog/delegate-dialog.component';
import { RevokeDelegationDialogComponent } from '../revoke-delegation-dialog/revoke-delegation-dialog.component';
import { DelegationsActions } from '../store/delegations.actions';
import { delegationsFeature } from '../store/delegations.feature';

/** The delegation in the pane and the side it was found on. */
interface Shown {
  delegation: Delegation;
  given: boolean;
}

/**
 * Reads the delegation id of the URL.
 *
 * @param value the `delegation` query parameter
 * @returns the id, or null when absent or not a positive whole number
 */
export function delegationOf(value: string | null): number | null {
  const id = Number(value);
  return value && Number.isInteger(id) && id > 0 ? id : null;
}

@Component({
  selector: 'app-delegation-list',
  imports: [
    NgTemplateOutlet,
    MatChip,
    MatButton,
    IconComponent,
    SkeletonRowsComponent,
    PageHeaderComponent,
    SplitComponent,
    PaneComponent,
    ListRowComponent,
    ListRowsDirective,
    RouterLink,
    TranslocoPipe,
    KyivDatePipe,
  ],
  templateUrl: './delegation-list.component.html',
  styleUrl: './delegation-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DelegationListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly language = inject(LanguageService);
  private lastShownId: number | null = null;

  protected readonly canDelegate = canDelegate(this.auth.permissions());

  readonly given = this.store.selectSignal(delegationsFeature.selectGiven);
  readonly received = this.store.selectSignal(delegationsFeature.selectReceived);
  readonly loading = this.store.selectSignal(delegationsFeature.selectLoading);
  /** True while the lists load and nothing is shown yet: skeleton rows stand in for both lists. */
  readonly firstLoad = computed(
    () => this.loading() && this.given().length === 0 && this.received().length === 0,
  );
  readonly problem = this.store.selectSignal(delegationsFeature.selectProblem);
  readonly revokingId = this.store.selectSignal(delegationsFeature.selectRevokingId);
  readonly revokeProblem = this.store.selectSignal(delegationsFeature.selectRevokeProblem);
  readonly message = this.store.selectSignal(delegationsFeature.selectMessage);

  /** The delegation chosen with `?delegation=`, null when none is. */
  readonly selectedId = toSignal(
    this.route.queryParamMap.pipe(map((params) => delegationOf(params.get('delegation')))),
    { initialValue: delegationOf(this.route.snapshot.queryParamMap.get('delegation')) },
  );
  readonly shown = computed<Shown | null>(() => {
    const id = this.selectedId();
    const given = this.given().find((delegation) => delegation.id === id);
    if (given) {
      return { delegation: given, given: true };
    }
    const received = this.received().find((delegation) => delegation.id === id);
    return received ? { delegation: received, given: false } : null;
  });
  /** A chosen delegation that the loaded lists do not hold. */
  readonly notFound = computed(
    () => this.selectedId() !== null && !this.loading() && !this.problem() && !this.shown(),
  );
  readonly capabilities = computed(() => {
    const shown = this.shown();
    return shown ? capabilitiesOf(shown.delegation.role) : null;
  });
  readonly remaining = computed(() => {
    const shown = this.shown();
    const left = shown ? daysLeft(shown.delegation, kyivToday()) : null;
    return left
      ? {
          key: `delegations.detail.${left.key}.${pluralForm(left.count, this.language.current())}`,
          count: left.count,
        }
      : null;
  });

  constructor() {
    effect(() => {
      const shown = this.shown();
      if (shown) {
        this.lastShownId = shown.delegation.id;
      } else if (this.notFound() && this.selectedId() === this.lastShownId) {
        this.lastShownId = null;
        this.select(null);
        this.snackBar.open(this.transloco.translate('delegations.detail.gone'), undefined, {
          duration: 5000,
        });
      }
    });
  }

  ngOnInit(): void {
    this.store.dispatch(DelegationsActions.opened());
  }

  /**
   * Shows a delegation in the pane and keeps it in the URL; null goes back to the lists.
   *
   * @param id the delegation id
   */
  select(id: number | null): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { delegation: id },
      queryParamsHandling: 'merge',
    });
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

  revocable(delegation: Delegation): boolean {
    return delegation.state === 'active' || delegation.state === 'upcoming';
  }

  problemFor(delegation: Delegation): string | null {
    const problem = this.revokeProblem();
    return problem && problem.id === delegation.id
      ? `delegations.problems.${problem.problem}`
      : null;
  }

  person(user: UserBrief): string {
    return `${user.lastName} ${user.firstName}`;
  }

  initialsOf(user: UserBrief): string {
    return initials(this.person(user));
  }

  name(organization: OrganizationRef): string {
    return organizationName(organization, this.language.current());
  }
}
