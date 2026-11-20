import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Store, provideState, provideStore } from '@ngrx/store';
import { BehaviorSubject, NEVER, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { readPermissions } from '../../../core/auth/permissions';
import { LanguageService } from '../../../core/i18n/language.service';
import { LegalHoldsService } from '../legal-holds.service';
import { PeopleService } from '../people.service';
import { RevokeRoleDialogComponent } from '../revoke-role-dialog/revoke-role-dialog.component';
import { AdminUsersActions } from '../store/admin-users.actions';
import { adminUsersFeature } from '../store/admin-users.feature';
import { UserDetail, UsersService } from '../users.service';
import { UserDetailComponent } from './user-detail.component';

const faculty = {
  id: 9,
  name: 'Faculty of Mathematics',
  nameUk: 'Факультет математики',
  code: 'FMI',
  type: 'FACULTY' as const,
};

const department = {
  id: 64,
  name: 'Department of Algebra',
  nameUk: 'Кафедра алгебри',
  code: 'DAI',
  type: 'DEPARTMENT' as const,
};

const user: UserDetail = {
  id: 7,
  email: 'employee.fmi@chnu.edu.ua',
  firstName: 'Анастасія',
  lastName: 'Працівник',
  organization: department,
  status: 'ACTIVE',
  roles: [
    { id: 3, role: 'EMPLOYEE', organization: department, validFrom: '2026-09-01', validTo: null },
  ],
  membershipConfirmed: true,
  createdAt: '2026-09-01T00:00:00Z',
  lastLoginAt: null,
  roleHistory: [
    { id: 3, role: 'EMPLOYEE', organization: department, validFrom: '2026-09-01', validTo: null },
    {
      id: 2,
      role: 'FACULTY_SECRETARY',
      organization: faculty,
      validFrom: '2025-01-01',
      validTo: '2025-12-31',
    },
  ],
};

const translations = {
  uk: {
    roles: { EMPLOYEE: 'Працівник', FACULTY_SECRETARY: 'Секретар факультету' },
    admin: {
      status: { ACTIVE: 'Активний' },
      people: { unknownAddress: 'Адреса невідома' },
      detail: {
        back: 'До списку',
        email: 'Пошта',
        organization: 'Підрозділ',
        status: 'Статус',
        created: 'Створено',
        lastLogin: 'Останній вхід',
        never: 'Ще не входив',
        currentRoles: 'Чинні ролі',
        history: 'Історія ролей',
        noRoles: 'Ролей немає',
        noHistory: 'Історія порожня',
        validity: 'діє з {{from}}',
        validityUntil: 'діє з {{from}} до {{to}}',
        ended: 'Завершено',
        assign: 'Призначити роль',
        revoke: 'Відкликати',
        assigned: 'Роль призначено.',
        revoked: 'Роль відкликано.',
        sessionHint: 'Нова роль зʼявиться після наступного оновлення токена або входу.',
        notFound: 'Користувача не знайдено',
        notFoundHint: 'Такого користувача немає.',
      },
      problems: {
        'role-last-own': 'Не можна відкликати власну останню роль',
        network: 'Сервер недоступний',
      },
    },
  },
};

function token(claims: Record<string, unknown>): string {
  return `header.${btoa(JSON.stringify(claims))}.signature`;
}

describe('UserDetailComponent', () => {
  let fixture: ComponentFixture<UserDetailComponent>;
  let store: Store;
  let dialog: { open: ReturnType<typeof vi.fn> };
  let api: { revokeRole: ReturnType<typeof vi.fn> };
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  beforeEach(async () => {
    params = new BehaviorSubject(convertToParamMap({ id: '7' }));
    dialog = { open: vi.fn() };
    api = { revokeRole: vi.fn().mockReturnValue(of(undefined)) };
    await TestBed.configureTestingModule({
      imports: [
        UserDetailComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideRouter([]),
        provideStore(),
        provideState(adminUsersFeature),
        { provide: MatDialog, useValue: dialog },
        { provide: UsersService, useValue: api },
        { provide: PeopleService, useValue: { get: vi.fn().mockReturnValue(NEVER) } },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        {
          provide: AuthService,
          useValue: { permissions: signal(readPermissions(token({ role_scopes: ['DEAN:9'] }))) },
        },
        {
          provide: ActivatedRoute,
          useValue: { paramMap: params },
        },
      ],
    }).compileComponents();
    store = TestBed.inject(Store);
    fixture = TestBed.createComponent(UserDetailComponent);
    fixture.detectChanges();
  });

  it('ac2_10_shows_the_profile_current_roles_and_the_role_history_with_ended_rows_marked', () => {
    store.dispatch(AdminUsersActions.userLoaded({ user }));
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('[data-testid="detail-email"]')?.textContent).toContain(
      'employee.fmi@chnu.edu.ua',
    );
    expect(element.querySelector('[data-testid="current-roles"]')?.textContent).toContain(
      'Працівник · Кафедра алгебри',
    );
    expect(element.querySelector('[data-testid="current-roles"]')?.textContent).toContain(
      'діє з 01.09.2026',
    );
    expect(element.textContent).toContain('01.09.2026, 03:00');
    expect(element.textContent).not.toContain('2026-09-01T00:00:00Z');
    const history = Array.from(
      element.querySelectorAll<HTMLElement>('[data-testid="role-history"] li'),
    );

    expect(history).toHaveLength(2);
    expect(history[0].classList).not.toContain('admin__role--ended');
    expect(history[1].classList).toContain('admin__role--ended');
    expect(history[1].textContent).toContain('діє з 01.01.2025 до 31.12.2025');
  });

  it('ac2_10_shows_a_not_found_state_for_an_unknown_user_without_an_error_loop', () => {
    store.dispatch(AdminUsersActions.userLoadFailed({ problem: 'unknown', notFound: true }));
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="detail-not-found"]').textContent,
    ).toContain('Користувача не знайдено');
    expect(fixture.nativeElement.querySelector('[data-testid="detail-profile"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="detail-error"]')).toBeNull();
  });

  it('ac9_1_an_unknown_user_shows_the_not_found_card', () => {
    store.dispatch(AdminUsersActions.userLoadFailed({ problem: 'unknown', notFound: true }));
    fixture.detectChanges();

    const card = fixture.nativeElement.querySelector(
      'app-status-page[data-testid="detail-not-found"]',
    );
    expect(card?.querySelector('h2')?.textContent).toContain('Користувача не знайдено');
  });

  it('ac2_9_states_that_a_new_role_reaches_the_session_with_the_next_refresh_or_sign_in', () => {
    store.dispatch(AdminUsersActions.userLoaded({ user }));
    fixture.detectChanges();
    dialog.open.mockReturnValue({ afterClosed: () => of(user.roles[0]) });
    const dispatch = vi.spyOn(store, 'dispatch');

    fixture.nativeElement.querySelector('[data-testid="assign-role-open"]').click();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="detail-message"]').textContent,
    ).toContain('Роль призначено.');
    expect(
      fixture.nativeElement.querySelector('[data-testid="detail-session-hint"]').textContent,
    ).toContain('після наступного оновлення токена або входу');
    expect(dispatch).toHaveBeenCalledWith(AdminUsersActions.userReloaded({ id: 7 }));
  });

  it('ac2_10_revokes_a_role_only_after_the_confirmation_dialog', () => {
    store.dispatch(AdminUsersActions.userLoaded({ user }));
    fixture.detectChanges();
    dialog.open.mockReturnValue({ afterClosed: () => of(false) });

    fixture.nativeElement.querySelector('[data-testid="revoke-role"]').click();

    expect(dialog.open).toHaveBeenCalledWith(RevokeRoleDialogComponent, {
      data: { assignment: user.roles[0], name: 'Анастасія Працівник' },
      width: '480px',
    });
    expect(api.revokeRole).not.toHaveBeenCalled();

    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    fixture.nativeElement.querySelector('[data-testid="revoke-role"]').click();
    fixture.detectChanges();

    expect(api.revokeRole).toHaveBeenCalledWith(7, 3);
    expect(
      fixture.nativeElement.querySelector('[data-testid="detail-message"]').textContent,
    ).toContain('Роль відкликано.');
  });

  it('ac2_10_renders_a_refused_revocation_inline', () => {
    store.dispatch(AdminUsersActions.userLoaded({ user }));
    fixture.detectChanges();
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    api.revokeRole.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            error: { type: 'urn:awards:problem:role-last-own' },
            status: 403,
          }),
      ),
    );

    fixture.nativeElement.querySelector('[data-testid="revoke-role"]').click();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="detail-action-error"]').textContent,
    ).toContain('Не можна відкликати власну останню роль');
    expect(fixture.nativeElement.querySelector('[data-testid="detail-message"]')).toBeNull();
  });

  it('ac3_6_shows_no_legal_hold_card_without_the_permission', () => {
    store.dispatch(AdminUsersActions.userLoaded({ user }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="legal-hold-card"]')).toBeNull();
  });

  it('ac1_9_shows_the_person_card_and_no_role_assignment_for_a_person_without_an_account', () => {
    store.dispatch(
      AdminUsersActions.userLoaded({
        user: {
          ...user,
          status: 'UNCLAIMED',
          email: 'unclaimed-1f2e@unclaimed.invalid',
          roles: [],
          roleHistory: [],
        },
      }),
    );
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('app-person-card')).not.toBeNull();
    expect(element.querySelector('[data-testid="assign-role-open"]')).toBeNull();
    expect(element.querySelector('[data-testid="detail-email"]')?.textContent).toContain(
      'Адреса невідома',
    );
  });

  it('ac1_9_shows_no_person_card_for_an_account', () => {
    store.dispatch(AdminUsersActions.userLoaded({ user }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-person-card')).toBeNull();
  });

  it('ac4_1_follows_the_selected_id_and_names_the_person_below_the_page_heading', () => {
    const dispatch = vi.spyOn(store, 'dispatch');
    store.dispatch(AdminUsersActions.userLoaded({ user }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('h1')).toBeNull();
    expect(fixture.nativeElement.querySelector('h2.admin__name')).not.toBeNull();

    params.next(convertToParamMap({ id: '9' }));

    expect(dispatch).toHaveBeenCalledWith(AdminUsersActions.userOpened({ id: 9 }));
  });

  it('ac2_7_ac4_5_confirms_membership_in_the_pane_with_the_own_department', () => {
    store.dispatch(AdminUsersActions.userLoaded({ user: { ...user, membershipConfirmed: false } }));
    fixture.detectChanges();
    const dispatch = vi.spyOn(store, 'dispatch');

    fixture.nativeElement.querySelector('[data-testid="confirm-membership"]').click();

    expect(dispatch).toHaveBeenCalledWith(
      AdminUsersActions.membershipConfirmed({ id: 7, organizationId: user.organization.id }),
    );
  });

  it('ac4_2_links_the_chosen_colleague_instead_of_a_plain_confirmation', () => {
    store.dispatch(AdminUsersActions.userLoaded({ user: { ...user, membershipConfirmed: false } }));
    fixture.detectChanges();
    dialog.open.mockReturnValue({
      afterClosed: () => of({ organizationId: 64, person: { id: 41, version: 3 } }),
    });
    const dispatch = vi.spyOn(store, 'dispatch');

    fixture.nativeElement.querySelector('[data-testid="correct-department"]').click();

    expect(dispatch).toHaveBeenCalledWith(
      AdminUsersActions.membershipLinked({ id: 7, personId: 41, version: 3 }),
    );
  });

  it('ac2_7_renders_a_refused_confirmation_and_hides_the_actions_once_confirmed', () => {
    store.dispatch(AdminUsersActions.userLoaded({ user: { ...user, membershipConfirmed: false } }));
    store.dispatch(
      AdminUsersActions.membershipConfirmFailed({ id: 7, problem: 'role-already-assigned' }),
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="confirm-error"]')).not.toBeNull();

    store.dispatch(AdminUsersActions.userLoaded({ user }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="confirm-membership"]')).toBeNull();
  });

  it('ac4_5_keeps_an_answer_about_the_previous_person_out_of_the_pane', () => {
    const answer = new Subject<void>();
    api.revokeRole.mockReturnValue(answer);
    store.dispatch(AdminUsersActions.userLoaded({ user }));
    fixture.detectChanges();
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    fixture.nativeElement.querySelector('[data-testid="revoke-role"]').click();
    params.next(convertToParamMap({ id: '9' }));
    const dispatch = vi.spyOn(store, 'dispatch');

    answer.next();
    fixture.detectChanges();

    expect(fixture.componentInstance.message()).toBeNull();
    expect(dispatch).toHaveBeenCalledWith(AdminUsersActions.refreshed());
    expect(dispatch).not.toHaveBeenCalledWith(AdminUsersActions.userReloaded({ id: 7 }));
  });

  it('ac4_5_refreshes_the_list_row_after_a_revocation', () => {
    store.dispatch(AdminUsersActions.userLoaded({ user }));
    fixture.detectChanges();
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    const dispatch = vi.spyOn(store, 'dispatch');

    fixture.nativeElement.querySelector('[data-testid="revoke-role"]').click();

    expect(dispatch).toHaveBeenCalledWith(AdminUsersActions.userReloaded({ id: 7 }));
    expect(dispatch).toHaveBeenCalledWith(AdminUsersActions.refreshed());
  });
});

describe('UserDetailComponent for a GDPR officer', () => {
  it('ac3_6_shows_the_legal_hold_card_of_the_opened_user', async () => {
    const holds = { list: vi.fn().mockReturnValue(of({ holds: [], erasureScheduledFor: null })) };
    await TestBed.configureTestingModule({
      imports: [
        UserDetailComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideRouter([]),
        provideStore(),
        provideState(adminUsersFeature),
        { provide: MatDialog, useValue: { open: vi.fn() } },
        { provide: UsersService, useValue: {} },
        { provide: LegalHoldsService, useValue: holds },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        {
          provide: AuthService,
          useValue: {
            permissions: signal(
              readPermissions(
                token({ permissions: ['user:read:all', 'legal-hold:manage'], role_scopes: [] }),
              ),
            ),
          },
        },
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ id: '7' })) },
        },
      ],
    }).compileComponents();
    const store = TestBed.inject(Store);
    const fixture = TestBed.createComponent(UserDetailComponent);
    fixture.detectChanges();
    store.dispatch(AdminUsersActions.userLoaded({ user }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="legal-hold-card"]')).not.toBeNull();
    expect(holds.list).toHaveBeenCalledWith(7);
  });
});
