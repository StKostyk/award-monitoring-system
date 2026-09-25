import { computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { DelegationsService } from '../../features/delegations/delegations.service';
import { AuthService } from '../auth/auth.service';
import { NO_PERMISSIONS, readPermissions } from '../auth/permissions';
import { LanguageService } from '../i18n/language.service';
import { ShellComponent } from './shell.component';

const dean = { id: 2, firstName: 'Мартин', lastName: 'Мартинюк', email: 'dean.fmi@chnu.edu.ua' };

function delegation(state: 'active' | 'revoked') {
  return {
    id: 1,
    role: 'DEAN' as const,
    organization: {
      id: 9,
      name: 'Faculty',
      nameUk: 'Факультет математики',
      code: 'FMI',
      type: 'FACULTY' as const,
    },
    delegator: dean,
    delegate: { id: 3, firstName: 'Аліна', lastName: 'Секретар', email: 'secretary.fmi@chnu.edu.ua' },
    validFrom: '2026-09-24',
    validTo: '2026-10-08',
    reason: null,
    state,
    createdAt: '2026-09-24T08:00:00Z',
    revokedAt: null,
  };
}

function tokenWith(claims: Record<string, unknown>): string {
  return `header.${btoa(JSON.stringify(claims))}.signature`;
}

describe('ShellComponent', () => {
  const auth = {
    isAuthenticated: signal(true),
    fullName: computed(() => 'Martyn Martyniuk'),
    permissions: signal(NO_PERMISSIONS),
    logout: vi.fn().mockResolvedValue(undefined),
  };
  const language = { toggle: vi.fn(), current: () => 'uk' };
  const delegations = { list: vi.fn().mockReturnValue(of({ given: [], received: [] })) };

  beforeEach(async () => {
    auth.logout.mockClear();
    auth.permissions.set(NO_PERMISSIONS);
    language.toggle.mockClear();
    delegations.list.mockClear();
    delegations.list.mockReturnValue(of({ given: [], received: [] }));
    await TestBed.configureTestingModule({
      imports: [
        ShellComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            uk: {
              app: { title: 'Нагороди', language: 'EN', logout: 'Вийти' },
              admin: { users: { title: 'Користувачі' } },
              delegations: {
                title: 'Мої делегування',
                actingFor: 'Діє за дорученням: {{name}}',
              },
            },
          },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: LanguageService, useValue: language },
        { provide: DelegationsService, useValue: delegations },
      ],
    }).compileComponents();
  });

  it('ac12 shows the signed-in user and the logout button', () => {
    auth.isAuthenticated.set(true);
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('[data-testid="user-name"]')?.textContent).toContain('Martyn Martyniuk');
    (element.querySelector('[data-testid="logout"]') as HTMLButtonElement).click();

    expect(auth.logout).toHaveBeenCalled();
  });

  it('ac17 toggles the language from the toolbar', () => {
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="language-toggle"]') as HTMLButtonElement).click();

    expect(language.toggle).toHaveBeenCalled();
  });

  it('ac2_10_shows_the_users_entry_only_to_callers_who_may_open_the_directory', () => {
    auth.isAuthenticated.set(true);
    const anonymous = TestBed.createComponent(ShellComponent);
    anonymous.detectChanges();

    expect(anonymous.nativeElement.querySelector('[data-testid="nav-users"]')).toBeNull();

    auth.permissions.set(readPermissions(tokenWith({ permissions: ['user:read:scope'] })));
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    const link = fixture.nativeElement.querySelector('[data-testid="nav-users"]');

    expect(link.getAttribute('href')).toBe('/admin/users');
    expect(link.textContent).toContain('Користувачі');
  });

  it('ac3_6_shows_the_delegations_entry_only_to_a_holder_of_an_approval_role', () => {
    auth.isAuthenticated.set(true);
    auth.permissions.set(readPermissions(tokenWith({ role_scopes: ['EMPLOYEE:64'] })));
    const employee = TestBed.createComponent(ShellComponent);
    employee.detectChanges();

    expect(employee.nativeElement.querySelector('[data-testid="nav-delegations"]')).toBeNull();

    auth.permissions.set(readPermissions(tokenWith({ role_scopes: ['DEAN:9'] })));
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    const link = fixture.nativeElement.querySelector('[data-testid="nav-delegations"]');

    expect(link.getAttribute('href')).toBe('/delegations');
    expect(link.textContent).toContain('Мої делегування');
  });

  it('ac3_6_names_the_delegator_while_a_received_delegation_is_active', () => {
    auth.isAuthenticated.set(true);
    auth.permissions.set(
      readPermissions(tokenWith({ role_scopes: ['FACULTY_SECRETARY:9', 'DEAN:9'], delegations: ['DEAN:9:2'] })),
    );
    delegations.list.mockReturnValue(of({ given: [], received: [delegation('active')] }));
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    expect(delegations.list).toHaveBeenCalledWith('active');
    expect(fixture.nativeElement.querySelector('[data-testid="acting-for"]').textContent).toContain(
      'Діє за дорученням: Мартин Мартинюк',
    );
  });

  it('ac3_2_ac3_3_keeps_the_banner_away_without_a_delegation_in_the_token', () => {
    auth.isAuthenticated.set(true);
    auth.permissions.set(readPermissions(tokenWith({ role_scopes: ['FACULTY_SECRETARY:9'] })));
    delegations.list.mockReturnValue(of({ given: [], received: [delegation('revoked')] }));
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    expect(delegations.list).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[data-testid="acting-for"]')).toBeNull();
  });

  it('hides the user controls when anonymous', () => {
    auth.isAuthenticated.set(false);
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="logout"]')).toBeNull();
  });
});
