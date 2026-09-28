import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { NO_PERMISSIONS, readPermissions } from '../../core/auth/permissions';
import { awardEntryGuard, ownAwardsGuard, unsavedChangesGuard } from './awards.guards';

function tokenWith(claims: Record<string, unknown>): string {
  return `header.${btoa(JSON.stringify(claims))}.signature`;
}

describe('award guards', () => {
  const auth = { permissions: signal(NO_PERMISSIONS) };
  const route = {} as ActivatedRouteSnapshot;
  const state = { url: '/awards' } as RouterStateSnapshot;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
  });

  it('ac1_9_opens_own_awards_with_award_read_own', () => {
    auth.permissions.set(readPermissions(tokenWith({ permissions: ['award:read:own'] })));

    expect(TestBed.runInInjectionContext(() => ownAwardsGuard(route, state))).toBe(true);
    const refused = TestBed.runInInjectionContext(() => awardEntryGuard(route, state)) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(refused)).toBe('/forbidden');
  });

  it('ac1_2_opens_the_form_only_with_award_create', () => {
    auth.permissions.set(
      readPermissions(tokenWith({ permissions: ['award:read:own', 'award:create'] })),
    );

    expect(TestBed.runInInjectionContext(() => awardEntryGuard(route, state))).toBe(true);
  });

  it('ac1_9_refuses_own_awards_without_any_award_permission', () => {
    auth.permissions.set(NO_PERMISSIONS);

    const refused = TestBed.runInInjectionContext(() => ownAwardsGuard(route, state)) as UrlTree;

    expect(TestBed.inject(Router).serializeUrl(refused)).toBe('/forbidden');
  });

  it('ac1_10_asks_the_page_before_leaving', () => {
    const page = { confirmLeave: () => false };

    expect(unsavedChangesGuard(page, route, state, state)).toBe(false);
  });
});
