import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { OnboardingService } from './onboarding.service';
import { employeeState } from './onboarding.testing';

const url = `${environment.apiUrl}/users/me/onboarding`;

describe('OnboardingService', () => {
  let service: OnboardingService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OnboardingService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function loaded(state = employeeState()): Promise<void> {
    const loading = service.load();
    http.expectOne(url).flush(state);
    await loading;
  }

  it('ac9_4_reads_the_steps_of_the_caller', async () => {
    await loaded();

    expect(service.state()?.steps.map((step) => step.key)).toEqual([
      'NOTICE',
      'PROFILE',
      'FIRST_AWARD',
      'VISIBILITY',
      'NOTIFICATIONS',
    ]);
  });

  it('ac9_4_leaves_no_state_when_the_read_fails', async () => {
    const loading = service.load();
    http.expectOne(url).flush(null, { status: 500, statusText: 'Server Error' });
    await loading;

    expect(service.state()).toBeNull();
  });

  it('ac9_4_stores_an_opened_step_with_the_stored_ones_and_never_the_derived', async () => {
    await loaded();

    const saving = service.markDone('VISIBILITY');
    const request = http.expectOne(url);
    request.flush(employeeState());
    await saving;

    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      done: ['PROFILE', 'VISIBILITY'],
      dismissed: false,
      tourSeen: false,
    });
  });

  it('ac9_4_sends_nothing_for_a_derived_step_or_one_already_done', async () => {
    await loaded();

    await service.markDone('FIRST_AWARD');
    await service.markDone('PROFILE');
    await service.markDone('IMPORT');

    http.expectNone(url);
  });

  it('ac9_4_hides_the_checklist_for_good', async () => {
    await loaded();

    const saving = service.dismiss();
    const request = http.expectOne(url);
    request.flush(employeeState({ dismissed: true }));
    await saving;

    expect(request.request.body).toEqual({ done: ['PROFILE'], dismissed: true, tourSeen: false });
    expect(service.state()?.dismissed).toBe(true);
  });

  it('ac9_5_records_the_tour_once', async () => {
    await loaded();

    const saving = service.markTourSeen();
    const request = http.expectOne(url);
    request.flush(employeeState({ tourSeen: true }));
    await saving;
    await service.markTourSeen();

    expect(request.request.body).toEqual({ done: ['PROFILE'], dismissed: false, tourSeen: true });
  });

  it('ac9_3_reads_again_when_a_save_is_refused', async () => {
    await loaded();

    const saving = service.markDone('NOTIFICATIONS');
    http.expectOne({ method: 'PUT', url }).flush(null, { status: 400, statusText: 'Bad Request' });
    await Promise.resolve();
    await Promise.resolve();
    http.expectOne({ method: 'GET', url }).flush(employeeState({ steps: [] }));
    await saving;

    expect(service.state()?.steps).toEqual([]);
  });
});
