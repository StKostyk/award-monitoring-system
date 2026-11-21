import {
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  OnInit,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatPaginator, MatPaginatorIntl, PageEvent } from '@angular/material/paginator';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatOption, MatSelect } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ofType } from '@ngrx/effects';
import { ScannedActionsSubject, Store } from '@ngrx/store';
import {
  Observable,
  catchError,
  debounceTime,
  distinctUntilChanged,
  filter,
  forkJoin,
  map,
  of,
  startWith,
} from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { canManagePeople } from '../../../core/auth/permissions';
import { AccountStatus, OrganizationRef, RoleType } from '../../../core/auth/user-profile';
import { LanguageService } from '../../../core/i18n/language.service';
import { IconComponent } from '../../../shared/icons/icon.component';
import { watchLeftSelection } from '../../../shared/layout/left-selection';
import { ListRowComponent, ListRowsDirective } from '../../../shared/layout/list-row.component';
import { PageHeaderComponent } from '../../../shared/layout/page-header.component';
import { PaneComponent } from '../../../shared/layout/pane.component';
import { SplitComponent } from '../../../shared/layout/split.component';
import { organizationName } from '../../../shared/organization-name';
import { TranslatedPaginatorIntl } from '../../../shared/translated-paginator-intl';
import { initials } from '../../../shared/widgets/top-widget.component';
import { Person, placeholderAddress } from '../people.service';
import { PersonDialogComponent, PersonDialogData } from '../person-dialog/person-dialog.component';
import { ROLES, STATUSES } from '../role-organizations';
import { AdminUsersActions } from '../store/admin-users.actions';
import { NO_FILTERS, adminUsersFeature } from '../store/admin-users.feature';
import { UserFilters, UserSummary, UsersService } from '../users.service';

const DEBOUNCE = 300;
const MINIMUM_QUERY = 2;

/** The counts the header names: accounts in scope, waiting for a membership check, blocked. */
export interface UserCounts {
  total: number | null;
  waiting: number | null;
  blocked: number | null;
}

/**
 * The users page: the people of the caller's scope in a list with filters, and the selected person in the pane
 * beside it (the child route `:id`); the header counts the accounts, those waiting for a membership check and the
 * blocked ones.
 */
@Component({
  selector: 'app-user-list',
  imports: [
    ReactiveFormsModule,
    MatFormField,
    MatLabel,
    MatHint,
    MatInput,
    MatSelect,
    MatOption,
    MatCheckbox,
    MatPaginator,
    MatProgressBar,
    MatButton,
    IconComponent,
    ListRowComponent,
    ListRowsDirective,
    PageHeaderComponent,
    PaneComponent,
    RouterLink,
    RouterOutlet,
    SplitComponent,
    TranslocoPipe,
  ],
  providers: [{ provide: MatPaginatorIntl, useClass: TranslatedPaginatorIntl }],
  templateUrl: './user-list.component.html',
  styleUrl: './user-list.component.scss',
})
export class UserListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly fb = inject(FormBuilder);
  private readonly dialog = inject(MatDialog);
  private readonly language = inject(LanguageService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(UsersService);
  private readonly transloco = inject(TranslocoService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly actions = inject(ScannedActionsSubject);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translation = toSignal(this.transloco.selectTranslation(), {
    initialValue: null,
  });
  private scrolledTo: number | null = null;

  protected readonly canAddPeople = canManagePeople(inject(AuthService).permissions());
  protected readonly roles = ROLES;
  protected readonly statuses = STATUSES;
  protected readonly pageSizes = [20, 50, 100];

  readonly users = this.store.selectSignal(adminUsersFeature.selectUsers);
  readonly total = this.store.selectSignal(adminUsersFeature.selectTotal);
  readonly page = this.store.selectSignal(adminUsersFeature.selectPage);
  readonly size = this.store.selectSignal(adminUsersFeature.selectSize);
  readonly loading = this.store.selectSignal(adminUsersFeature.selectLoading);
  readonly problem = this.store.selectSignal(adminUsersFeature.selectProblem);
  /** The person in the pane, from the address. */
  readonly selectedId = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      startWith(null),
      map(() => this.childId()),
    ),
    { initialValue: null },
  );
  readonly counted = signal<UserCounts>({ total: null, waiting: null, blocked: null });
  /** The context line: the counts that loaded, in the interface language. */
  readonly counts = computed(() => {
    this.translation();
    const counts = this.counted();
    const parts = (['total', 'waiting', 'blocked'] as const)
      .filter((key) => counts[key] !== null)
      .map((key) => this.transloco.translate(`admin.users.counts.${key}`, { count: counts[key] }));
    return parts.length === 0 ? null : parts.join(' · ');
  });

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
    effect(() => this.scrollToSelected(this.selectedId(), this.users()));
    let previous: number | null = null;
    let reloaded = false;
    effect(() => {
      const id = this.selectedId();
      if (previous !== null && id === null && !reloaded) {
        this.store.dispatch(AdminUsersActions.refreshed());
      }
      reloaded = false;
      previous = id;
    });
    watchLeftSelection(
      {
        selectedId: this.selectedId,
        ids: computed(() => this.users().map((user) => user.id)),
        settled: computed(() => !this.loading() && !this.problem()),
        check: (id) => this.api.get(id),
      },
      () => {
        reloaded = true;
        void this.router.navigate(['/admin/users']);
        this.snackBar.open(this.transloco.translate('admin.users.selectionGone'), undefined, {
          duration: 5000,
        });
      },
    );
  }

  ngOnInit(): void {
    this.store.dispatch(AdminUsersActions.opened());
    this.recount();
    this.actions
      .pipe(
        ofType(AdminUsersActions.reloaded, AdminUsersActions.refreshed),
        debounceTime(DEBOUNCE),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.recount());
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

  addPerson(): void {
    const data: PersonDialogData = { person: null };
    this.dialog
      .open(PersonDialogComponent, { data, width: '520px' })
      .afterClosed()
      .subscribe((person?: Person) => {
        if (person) {
          this.store.dispatch(AdminUsersActions.refreshed());
          void this.router.navigate(['/admin/users', person.id]);
        }
      });
  }

  /** The address a row carries for lookups; a placeholder of a person without an account stays out. */
  address(user: UserSummary): string | null {
    return user.status === 'UNCLAIMED' && placeholderAddress(user.email) ? null : user.email;
  }

  initialsOf(user: UserSummary): string {
    return initials(`${user.firstName} ${user.lastName}`);
  }

  name(organization: OrganizationRef): string {
    return organizationName(organization, this.language.current());
  }

  private childId(): number | null {
    const id = this.route.firstChild?.snapshot?.paramMap.get('id');
    return id ? Number(id) : null;
  }

  private recount(): void {
    forkJoin({
      total: this.count({}),
      waiting: this.count({ unconfirmed: true, status: 'ACTIVE' }),
      blocked: this.count({ status: 'SUSPENDED' }),
    }).subscribe((counts) => this.counted.set(counts));
  }

  private count(filters: Partial<UserFilters>): Observable<number | null> {
    return this.api.list({ ...NO_FILTERS, ...filters, page: 0, size: 1 }).pipe(
      map((page) => page.totalElements),
      catchError(() => of(null)),
    );
  }

  /** Brings the selected row into view once, when it is on the loaded page (a link opened directly). */
  private scrollToSelected(id: number | null, users: UserSummary[]): void {
    if (id === null || id === this.scrolledTo || !users.some((user) => user.id === id)) {
      return;
    }
    this.scrolledTo = id;
    afterNextRender(
      () =>
        this.host.nativeElement
          .querySelector('[data-testid="user-link"][aria-current="true"]')
          ?.scrollIntoView({ block: 'nearest' }),
      { injector: this.injector },
    );
  }
}
