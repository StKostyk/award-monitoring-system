import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { authGuard, delegationGuard, userDirectoryGuard } from './auth.guard';
import { AuthService } from './auth.service';
import { NO_PERMISSIONS, readPermissions } from './permissions';

function tokenWith(claims: Record<string, unknown>): string {
  return `header.${btoa(JSON.stringify(claims))}.signature`;
}

describe('authGuard', () => {
  const auth = {
    isAuthenticated: signal(false),
    permissions: signal(NO_PERMISSIONS),
    login: vi.fn(),
  };
  const state = { url: '/awards' } as RouterStateSnapshot;

  beforeEach(() => {
    auth.login.mockClear();
    auth.permissions.set(NO_PERMISSIONS);
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
  });

  it('ac11 sends an anonymous user to the authorization server with the requested url', () => {
    auth.isAuthenticated.set(false);

    const result = TestBed.runInInjectionContext(() => authGuard({} as ActivatedRouteSnapshot, state));

    expect(result).toBe(false);
    expect(auth.login).toHaveBeenCalledWith('/awards');
  });

  it('lets an authenticated user through', () => {
    auth.isAuthenticated.set(true);

    const result = TestBed.runInInjectionContext(() => authGuard({} as ActivatedRouteSnapshot, state));

    expect(result).toBe(true);
    expect(auth.login).not.toHaveBeenCalled();
  });

  it('ac2_10_opens_the_user_directory_only_with_a_read_permission', () => {
    auth.permissions.set(readPermissions(tokenWith({ permissions: ['user:read:all'] })));

    const allowed = TestBed.runInInjectionContext(() =>
      userDirectoryGuard({} as ActivatedRouteSnapshot, state),
    );

    expect(allowed).toBe(true);
  });

  it('ac2_10_sends_a_caller_without_the_permission_to_the_forbidden_page', () => {
    auth.permissions.set(readPermissions(tokenWith({ permissions: ['award:read:own'] })));

    const refused = TestBed.runInInjectionContext(() =>
      userDirectoryGuard({} as ActivatedRouteSnapshot, state),
    );

    expect(String(refused)).toBe(TestBed.inject(Router).createUrlTree(['/forbidden']).toString());
  });

  it('ac3_6_opens_the_delegations_page_for_an_approval_role_of_the_callers_own', () => {
    auth.permissions.set(readPermissions(tokenWith({ role_scopes: ['DEAN:9'] })));

    const allowed = TestBed.runInInjectionContext(() =>
      delegationGuard({} as ActivatedRouteSnapshot, state),
    );

    expect(allowed).toBe(true);
  });

  it('ac3_6_sends_an_employee_and_a_delegate_only_to_the_forbidden_page', () => {
    for (const claims of [
      { role_scopes: ['EMPLOYEE:64'] },
      { role_scopes: ['EMPLOYEE:64', 'DEAN:9'], delegations: ['DEAN:9:2'] },
    ]) {
      auth.permissions.set(readPermissions(tokenWith(claims)));

      const refused = TestBed.runInInjectionContext(() =>
        delegationGuard({} as ActivatedRouteSnapshot, state),
      );

      expect(String(refused)).toBe(TestBed.inject(Router).createUrlTree(['/forbidden']).toString());
    }
  });
});
