import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Store, provideState, provideStore } from '@ngrx/store';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { readPermissions } from '../../../core/auth/permissions';
import { LanguageService } from '../../../core/i18n/language.service';
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
  roles: [{ id: 3, role: 'EMPLOYEE', organization: department, validFrom: '2026-09-01', validTo: null }],
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
      problems: { 'role-last-own': 'Не можна відкликати власну останню роль', network: 'Сервер недоступний' },
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

  beforeEach(async () => {
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
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        {
          provide: AuthService,
          useValue: { permissions: signal(readPermissions(token({ role_scopes: ['DEAN:9'] }))) },
        },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '7' }) } } },
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
      'діє з 2026-09-01',
    );
    const history = Array.from(
      element.querySelectorAll<HTMLElement>('[data-testid="role-history"] li'),
    );

    expect(history).toHaveLength(2);
    expect(history[0].classList).not.toContain('admin__role--ended');
    expect(history[1].classList).toContain('admin__role--ended');
    expect(history[1].textContent).toContain('діє з 2025-01-01 до 2025-12-31');
  });

  it('ac2_10_shows_a_not_found_state_for_an_unknown_user_without_an_error_loop', () => {
    store.dispatch(AdminUsersActions.userLoadFailed({ problem: 'unknown', notFound: true }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="detail-not-found"]').textContent).toContain(
      'Користувача не знайдено',
    );
    expect(fixture.nativeElement.querySelector('[data-testid="detail-profile"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="detail-error"]')).toBeNull();
  });

  it('ac2_9_states_that_a_new_role_reaches_the_session_with_the_next_refresh_or_sign_in', () => {
    store.dispatch(AdminUsersActions.userLoaded({ user }));
    fixture.detectChanges();
    dialog.open.mockReturnValue({ afterClosed: () => of(user.roles[0]) });
    const dispatch = vi.spyOn(store, 'dispatch');

    fixture.nativeElement.querySelector('[data-testid="assign-role-open"]').click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="detail-message"]').textContent).toContain(
      'Роль призначено.',
    );
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
    expect(fixture.nativeElement.querySelector('[data-testid="detail-message"]').textContent).toContain(
      'Роль відкликано.',
    );
  });

  it('ac2_10_renders_a_refused_revocation_inline', () => {
    store.dispatch(AdminUsersActions.userLoaded({ user }));
    fixture.detectChanges();
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    api.revokeRole.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({ error: { type: 'urn:awards:problem:role-last-own' }, status: 403 }),
      ),
    );

    fixture.nativeElement.querySelector('[data-testid="revoke-role"]').click();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="detail-action-error"]').textContent,
    ).toContain('Не можна відкликати власну останню роль');
    expect(fixture.nativeElement.querySelector('[data-testid="detail-message"]')).toBeNull();
  });
});
