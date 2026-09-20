import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { vi } from 'vitest';

import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('authGuard', () => {
  const auth = { isAuthenticated: signal(false), login: vi.fn() };
  const state = { url: '/awards' } as RouterStateSnapshot;

  beforeEach(() => {
    auth.login.mockClear();
    TestBed.configureTestingModule({ providers: [{ provide: AuthService, useValue: auth }] });
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
});
