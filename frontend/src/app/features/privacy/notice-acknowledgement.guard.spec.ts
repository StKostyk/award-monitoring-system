import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { vi } from 'vitest';

import { AuthService } from '../../core/auth/auth.service';
import { ConsentOverview, ConsentsService } from './consents.service';
import { noticeAcknowledgementGuard } from './notice-acknowledgement.guard';

function overview(acknowledgementRequired: boolean): ConsentOverview {
  return {
    notice: {
      currentVersion: '1.0',
      acknowledgedVersion: acknowledgementRequired ? null : '1.0',
      acknowledgedAt: null,
      acknowledgementRequired,
    },
    consents: [],
    history: [],
  };
}

describe('noticeAcknowledgementGuard', () => {
  const auth = { isAuthenticated: signal(true) };
  const consents = { overview: vi.fn<() => Promise<ConsentOverview>>() };

  beforeEach(() => {
    auth.isAuthenticated.set(true);
    consents.overview.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: ConsentsService, useValue: consents },
      ],
    });
  });

  function run(url: string): Promise<boolean | UrlTree> {
    return TestBed.runInInjectionContext(
      () =>
        noticeAcknowledgementGuard(
          {} as ActivatedRouteSnapshot,
          { url } as RouterStateSnapshot,
        ) as Promise<boolean | UrlTree>,
    );
  }

  it('ac1_8 redirects to the acknowledgement page with the requested url', async () => {
    consents.overview.mockResolvedValue(overview(true));

    const result = await run('/awards?status=DRAFT');

    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe(
      '/privacy-notice?acknowledge=true&returnUrl=%2Fawards%3Fstatus%3DDRAFT',
    );
  });

  it('ac1_8 lets a person who acknowledged the current version through', async () => {
    consents.overview.mockResolvedValue(overview(false));

    expect(await run('/awards')).toBe(true);
  });

  it.each([
    '/privacy-notice?acknowledge=true',
    '/privacy',
    '/privacy/export',
    '/forbidden',
    '/not-found',
    '/offline?next=%2F',
  ])('ac1_8 keeps %s open without an acknowledgement', async (url) => {
    consents.overview.mockResolvedValue(overview(true));

    expect(await run(url)).toBe(true);
    expect(consents.overview).not.toHaveBeenCalled();
  });

  it('leaves anonymous visitors to the sign-in guard', async () => {
    auth.isAuthenticated.set(false);

    expect(await run('/awards')).toBe(true);
    expect(consents.overview).not.toHaveBeenCalled();
  });

  it('lets the navigation pass when the overview cannot be read', async () => {
    consents.overview.mockRejectedValue(new Error('offline'));

    expect(await run('/awards')).toBe(true);
  });

  it('does not exempt a page that only starts like an exempt one', async () => {
    consents.overview.mockResolvedValue(overview(true));

    expect(await run('/privacy-centre')).not.toBe(true);
  });
});
