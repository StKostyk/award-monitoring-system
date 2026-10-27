import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { connectivityInterceptor } from './connectivity.interceptor';
import { ConnectivityService } from './connectivity.service';

describe('connectivityInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let connectivity: ConnectivityService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([connectivityInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    connectivity = TestBed.inject(ConnectivityService);
  });

  afterEach(() => backend.verify());

  function failWithoutAnswer(url: string): void {
    http.get(url).subscribe({ error: () => undefined });
    backend.expectOne(url).error(new ProgressEvent('error'), { status: 0 });
  }

  it('ac1_7_marks_the_server_unreachable_when_an_api_request_gets_no_answer', () => {
    failWithoutAnswer(`${environment.apiUrl}/awards`);

    expect(connectivity.online()).toBe(false);
  });

  it('ac1_9_marks_the_server_reachable_again_on_the_next_answer', () => {
    failWithoutAnswer(`${environment.issuer}/.well-known/openid-configuration`);
    expect(connectivity.online()).toBe(false);

    http.get(`${environment.apiUrl}/users/me`).subscribe();
    backend.expectOne(`${environment.apiUrl}/users/me`).flush({});

    expect(connectivity.online()).toBe(true);
  });

  it('ac1_7_keeps_the_state_on_an_error_answer_of_the_server', () => {
    connectivity.unreachable();

    http.get(`${environment.apiUrl}/awards`).subscribe({ error: () => undefined });
    backend
      .expectOne(`${environment.apiUrl}/awards`)
      .flush({}, { status: 502, statusText: 'Bad Gateway' });

    expect(connectivity.online()).toBe(false);
  });

  it('ac1_7_ignores_shell_files_the_service_worker_may_answer_from_its_cache', () => {
    connectivity.unreachable();

    http.get('brand/brand.json').subscribe();
    backend.expectOne('brand/brand.json').flush({});

    expect(connectivity.online()).toBe(false);
  });

  it('ac2_9_backend_requests_bypass_a_controlling_service_worker', () => {
    Object.defineProperty(navigator, 'serviceWorker', {
      configurable: true,
      value: { controller: {} },
    });
    try {
      http.get(`${environment.apiUrl}/awards`).subscribe();
      http.get('brand/brand.json').subscribe();

      const api = backend.expectOne(`${environment.apiUrl}/awards`);
      const shell = backend.expectOne('brand/brand.json');
      expect(api.request.headers.get('ngsw-bypass')).toBe('true');
      expect(shell.request.headers.has('ngsw-bypass')).toBe(false);
      api.flush({});
      shell.flush({});
    } finally {
      Reflect.deleteProperty(navigator, 'serviceWorker');
    }
  });

  it('ac2_9_without_a_service_worker_requests_go_out_unchanged', () => {
    http.get(`${environment.apiUrl}/awards`).subscribe();

    const api = backend.expectOne(`${environment.apiUrl}/awards`);
    expect(api.request.headers.has('ngsw-bypass')).toBe(false);
    api.flush({});
  });
});
