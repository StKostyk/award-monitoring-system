import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Store } from '@ngrx/store';

import { AuthService } from '../../../core/auth/auth.service';
import { canDelegate } from '../../../core/auth/permissions';
import { OrganizationRef } from '../../../core/auth/user-profile';
import { LanguageService } from '../../../core/i18n/language.service';
import { kyivToday } from '../../../shared/date-format';
import { IconComponent } from '../../../shared/icons/icon.component';
import { KyivDatePipe } from '../../../shared/kyiv-date.pipe';
import { PageHeaderComponent } from '../../../shared/layout/page-header.component';
import { organizationName } from '../../../shared/organization-name';
import { pluralForm } from '../../../shared/plural';
import { SkeletonRowsComponent } from '../../../shared/skeleton/skeleton-rows.component';
import { initials } from '../../../shared/widgets/top-widget.component';
import { Capabilities, capabilitiesOf } from '../delegation-capabilities';
import { daysLeft } from '../delegation-days';
import { Delegation, UserBrief } from '../delegations.service';
import { DelegateDialogComponent } from '../delegate-dialog/delegate-dialog.component';
import { RevokeDelegationDialogComponent } from '../revoke-delegation-dialog/revoke-delegation-dialog.component';
import { DelegationsActions } from '../store/delegations.actions';
import { delegationsFeature } from '../store/delegations.feature';

/** Which delegations the board shows: given by the person or received by them. */
export type BoardSide = 'given' | 'received';

/** One board column: its key, the delegations it shows and how many it holds. */
export interface BoardColumn {
  key: 'active' | 'upcoming' | 'ended';
  delegations: readonly Delegation[];
  total: number;
}

/** Ended delegations shown before «Показати всі». */
export const ENDED_SHOWN = 5;

@Component({
  selector: 'app-delegation-list',
  imports: [
    MatButton,
    MatButtonToggle,
    MatButtonToggleGroup,
    IconComponent,
    SkeletonRowsComponent,
    PageHeaderComponent,
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
  private readonly auth = inject(AuthService);
  private readonly language = inject(LanguageService);
  private readonly chosenSide = signal<BoardSide | null>(null);
  /** Whether every ended delegation shows, not only the latest few. */
  readonly allEnded = signal(false);
  /** How many ended delegations show while the column is folded. */
  protected readonly endedShown = ENDED_SHOWN;

  /** The person whose page an administrator reads (`?delegatorId=`); null for the caller's own page. */
  protected readonly delegatorId = idOf(
    inject(ActivatedRoute).snapshot.queryParamMap.get('delegatorId'),
  );
  /** Another person's page is read-only: no new delegation and no revocation. */
  protected readonly canDelegate =
    this.delegatorId === null && canDelegate(this.auth.permissions());

  readonly given = this.store.selectSignal(delegationsFeature.selectGiven);
  readonly received = this.store.selectSignal(delegationsFeature.selectReceived);
  readonly loading = this.store.selectSignal(delegationsFeature.selectLoading);
  /** True while the lists load and nothing is shown yet: skeleton rows stand in for the board. */
  readonly firstLoad = computed(
    () => this.loading() && this.given().length === 0 && this.received().length === 0,
  );
  readonly problem = this.store.selectSignal(delegationsFeature.selectProblem);
  readonly revokingId = this.store.selectSignal(delegationsFeature.selectRevokingId);
  readonly revokeProblem = this.store.selectSignal(delegationsFeature.selectRevokeProblem);
  readonly message = this.store.selectSignal(delegationsFeature.selectMessage);

  /** The chosen side, «Від мене» until the first lists settle it. */
  readonly side = computed<BoardSide>(() => this.chosenSide() ?? 'given');
  readonly columns = computed<BoardColumn[]>(() => {
    const shown = this.side() === 'given' ? this.given() : this.received();
    const active = shown.filter((delegation) => delegation.state === 'active');
    const upcoming = shown.filter((delegation) => delegation.state === 'upcoming');
    const ended = shown
      .filter((delegation) => delegation.state === 'expired' || delegation.state === 'revoked')
      .sort((left, right) => endOf(right).localeCompare(endOf(left)) || right.id - left.id);
    return [
      { key: 'active', delegations: active, total: active.length },
      { key: 'upcoming', delegations: upcoming, total: upcoming.length },
      {
        key: 'ended',
        delegations: this.allEnded() ? ended : ended.slice(0, ENDED_SHOWN),
        total: ended.length,
      },
    ];
  });

  constructor() {
    effect(() => {
      const given = this.given().length;
      const received = this.received().length;
      if (this.chosenSide() === null && !this.loading() && given + received > 0) {
        this.chosenSide.set(given === 0 ? 'received' : 'given');
      }
    });
  }

  ngOnInit(): void {
    this.store.dispatch(DelegationsActions.opened({ delegatorId: this.delegatorId }));
  }

  /**
   * Switches the board between given and received delegations, the ended column folded again.
   *
   * @param side the side to show
   */
  show(side: BoardSide): void {
    this.chosenSide.set(side);
    this.allEnded.set(false);
  }

  delegate(): void {
    this.dialog
      .open(DelegateDialogComponent, { width: '560px' })
      .afterClosed()
      .subscribe((delegation?: Delegation) => {
        if (delegation) {
          this.show('given');
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

  /**
   * Whether the card offers «Відкликати»: a running or planned delegation the caller gave, on their own page.
   *
   * @param delegation the delegation on the card
   * @return true when the card shows the action
   */
  revocable(delegation: Delegation): boolean {
    return (
      this.delegatorId === null &&
      this.side() === 'given' &&
      (delegation.state === 'active' || delegation.state === 'upcoming')
    );
  }

  problemFor(delegation: Delegation): string | null {
    const problem = this.revokeProblem();
    return problem && problem.id === delegation.id
      ? `delegations.problems.${problem.problem}`
      : null;
  }

  /**
   * The person on the other end of a delegation: its delegate on the given side, its delegator on the received one.
   *
   * @param delegation the delegation on the card
   * @return the other person
   */
  other(delegation: Delegation): UserBrief {
    return this.side() === 'given' ? delegation.delegate : delegation.delegator;
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

  /**
   * The days until a running delegation ends or a planned one starts, as a key and a count.
   *
   * @param delegation the delegation on the card
   * @return the key and count, or null for an ended delegation
   */
  remaining(delegation: Delegation): { key: string; count: number } | null {
    const left = daysLeft(delegation, kyivToday());
    return left
      ? {
          key: `delegations.board.${left.key}.${pluralForm(left.count, this.language.current())}`,
          count: left.count,
        }
      : null;
  }

  limits(delegation: Delegation): Capabilities | null {
    return capabilitiesOf(delegation.role);
  }
}

/** The day a delegation ended: its revocation day in Kyiv, or its last day when it ran out. */
function endOf(delegation: Delegation): string {
  return delegation.revokedAt
    ? kyivToday(new Date(delegation.revokedAt))
    : delegation.validTo.slice(0, 10);
}

function idOf(value: string | null): number | null {
  const id = Number(value);
  return value !== null && Number.isInteger(id) && id > 0 ? id : null;
}
