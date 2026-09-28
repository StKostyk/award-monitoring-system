import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { vi } from 'vitest';

import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { unauthorizedInterceptor } from './unauthorized.interceptor';

describe('unauthorizedInterceptor', () => {
  const auth = { signedOutElsewhere: vi.fn(), refreshOnce: vi.fn() };
  const me = `${environment.apiUrl}/users/me`;
  const refused = { status: 401, statusText: 'Unauthorized' };
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    auth.signedOutElsewhere.mockClear();
    auth.refreshOnce.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([unauthorizedInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('ac4_6 a 401 is answered by one refresh and a repeat with the new token', async () => {
    auth.refreshOnce.mockResolvedValue('fresh-token');
    const call = firstValueFrom(http.get<{ id: number }>(me));

    backend.expectOne(me).flush({}, refused);
    await vi.waitFor(() => backend.expectOne(me).flush({ id: 7 }));

    await expect(call).resolves.toEqual({ id: 7 });
    expect(auth.refreshOnce).toHaveBeenCalledTimes(1);
    expect(auth.signedOutElsewhere).not.toHaveBeenCalled();
  });

  it('ac4_6 the repeat carries the renewed access token', async () => {
    auth.refreshOnce.mockResolvedValue('fresh-token');
    const call = firstValueFrom(http.get(me));

    backend.expectOne(me).flush({}, refused);
    await vi.waitFor(() => {
      const repeat = backend.expectOne(me);
      expect(repeat.request.headers.get('Authorization')).toBe('Bearer fresh-token');
      repeat.flush({});
    });
    await call;
  });

  it('ac65 a failed refresh ends the session and rethrows', async () => {
    auth.refreshOnce.mockResolvedValue(null);
    const call = firstValueFrom(http.get(me));

    backend.expectOne(me).flush({}, refused);

    await expect(call).rejects.toMatchObject({ status: 401 });
    expect(auth.signedOutElsewhere).toHaveBeenCalledTimes(1);
  });

  it('ac65 a repeat refused again ends the session without a second refresh', async () => {
    auth.refreshOnce.mockResolvedValue('fresh-token');
    const call = firstValueFrom(http.get(me));

    backend.expectOne(me).flush({}, refused);
    await vi.waitFor(() => backend.expectOne(me).flush({}, refused));

    await expect(call).rejects.toMatchObject({ status: 401 });
    expect(auth.refreshOnce).toHaveBeenCalledTimes(1);
    expect(auth.signedOutElsewhere).toHaveBeenCalledTimes(1);
  });

  it('ac4_6 a refresh that fails for another reason fails the request but keeps the session', async () => {
    auth.refreshOnce.mockRejectedValue(new Error('offline'));
    const call = firstValueFrom(http.get(me));

    backend.expectOne(me).flush({}, refused);

    await expect(call).rejects.toMatchObject({ status: 401 });
    expect(auth.signedOutElsewhere).not.toHaveBeenCalled();
  });

  it('ac4_6 hands the refused token to the refresh so a newer one is reused', async () => {
    auth.refreshOnce.mockResolvedValue(null);
    const call = firstValueFrom(http.get(me, { headers: { Authorization: 'Bearer old-token' } }));

    backend.expectOne(me).flush({}, refused);
    await expect(call).rejects.toMatchObject({ status: 401 });

    expect(auth.refreshOnce).toHaveBeenCalledWith('old-token');
  });

  it('ignores other statuses and other origins', async () => {
    const forbidden = firstValueFrom(http.get(`${environment.apiUrl}/awards`));
    backend
      .expectOne(`${environment.apiUrl}/awards`)
      .flush({}, { status: 403, statusText: 'Forbidden' });
    await expect(forbidden).rejects.toMatchObject({ status: 403 });

    const elsewhere = firstValueFrom(http.get('http://localhost:8025/api/v1/messages'));
    backend.expectOne('http://localhost:8025/api/v1/messages').flush({}, refused);
    await expect(elsewhere).rejects.toMatchObject({ status: 401 });

    expect(auth.refreshOnce).not.toHaveBeenCalled();
    expect(auth.signedOutElsewhere).not.toHaveBeenCalled();
  });
});
