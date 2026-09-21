import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { unauthorizedInterceptor } from './unauthorized.interceptor';

describe('unauthorizedInterceptor', () => {
  const auth = { signedOutElsewhere: vi.fn() };
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    auth.signedOutElsewhere.mockClear();
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

  it('ac65 a 401 from the API ends the session and rethrows', async () => {
    const call = http.get(`${environment.apiUrl}/users/me`).toPromise();
    backend.expectOne(`${environment.apiUrl}/users/me`).flush({}, { status: 401, statusText: 'Unauthorized' });

    await expect(call).rejects.toMatchObject({ status: 401 });
    expect(auth.signedOutElsewhere).toHaveBeenCalledTimes(1);
  });

  it('ignores other statuses and other origins', async () => {
    const forbidden = http.get(`${environment.apiUrl}/awards`).toPromise();
    backend.expectOne(`${environment.apiUrl}/awards`).flush({}, { status: 403, statusText: 'Forbidden' });
    await expect(forbidden).rejects.toMatchObject({ status: 403 });

    const elsewhere = http.get('http://localhost:8025/api/v1/messages').toPromise();
    backend.expectOne('http://localhost:8025/api/v1/messages').flush({}, { status: 401, statusText: 'Unauthorized' });
    await expect(elsewhere).rejects.toMatchObject({ status: 401 });

    expect(auth.signedOutElsewhere).not.toHaveBeenCalled();
  });
});
