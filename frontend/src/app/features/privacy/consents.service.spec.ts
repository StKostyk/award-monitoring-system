import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { vi } from 'vitest';

import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { ConsentOverview, ConsentsService, OVERVIEW_TTL_MS } from './consents.service';

const consentsUrl = `${environment.apiUrl}/users/me/consents`;

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

describe('ConsentsService', () => {
  const auth = { isAuthenticated: signal(true), userId: signal<string | null>('7') };
  let service: ConsentsService;
  let http: HttpTestingController;

  beforeEach(() => {
    auth.isAuthenticated.set(true);
    auth.userId.set('7');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    service = TestBed.inject(ConsentsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  async function read(answer?: ConsentOverview): Promise<ConsentOverview> {
    const result = service.overview();
    if (answer) {
      await Promise.resolve();
      http.expectOne(consentsUrl).flush(answer);
    }
    return result;
  }

  it('ac1_9 reads the public notice and the text of a version in a language', async () => {
    const notice = firstValueFrom(service.notice());
    http
      .expectOne(`${environment.apiUrl}/public/privacy-notice`)
      .flush({ version: '1.0', publishedOn: '2026-10-15', versions: [] });
    expect((await notice).version).toBe('1.0');

    const text = firstValueFrom(service.noticeText('1.0', 'en'));
    const request = http.expectOne('/privacy/1.0/en.html');
    expect(request.request.responseType).toBe('text');
    request.flush('<h2>Notice</h2>');
    expect(await text).toBe('<h2>Notice</h2>');
  });

  it('ac1_8 keeps the overview for fifteen minutes of one sign-in', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    expect((await read(overview(true))).notice.acknowledgementRequired).toBe(true);

    expect((await read()).notice.acknowledgementRequired).toBe(true);

    vi.advanceTimersByTime(OVERVIEW_TTL_MS);
    expect((await read(overview(false))).notice.acknowledgementRequired).toBe(false);
  });

  it('ac1_8 reads the overview again after a sign-out or for another person', async () => {
    await read(overview(true));

    auth.userId.set('8');
    await read(overview(false));

    auth.isAuthenticated.set(false);
    TestBed.tick();
    auth.isAuthenticated.set(true);
    await read(overview(false));
  });

  it('ac1_6 keeps the overview the acknowledgement answers', async () => {
    await read(overview(true));

    const answer = firstValueFrom(service.acknowledge('1.0'));
    const request = http.expectOne(`${consentsUrl}/notice-acknowledgement`);
    expect(request.request.body).toEqual({ version: '1.0' });
    request.flush(overview(false));
    await answer;

    expect((await read()).notice.acknowledgementRequired).toBe(false);
  });
});
