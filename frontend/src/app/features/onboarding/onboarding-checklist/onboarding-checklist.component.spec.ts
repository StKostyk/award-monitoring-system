import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/auth/auth.service';
import { translations } from '../../analytics/analytics.testing';
import { OnboardingState } from '../onboarding.service';
import { employeeState } from '../onboarding.testing';
import { OnboardingChecklistComponent } from './onboarding-checklist.component';

const url = `${environment.apiUrl}/users/me/onboarding`;

describe('OnboardingChecklistComponent', () => {
  const granted = signal<string[]>(['analytics:read:own']);
  const auth = {
    profile: signal({ firstName: 'Olena' }),
    permissions: signal({ hasPermission: (permission: string) => granted().includes(permission) }),
  };
  let fixture: ComponentFixture<OnboardingChecklistComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    granted.set(['analytics:read:own']);
    TestBed.configureTestingModule({
      imports: [OnboardingChecklistComponent, translations('en')],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', children: [] }]),
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OnboardingChecklistComponent);
  });

  afterEach(() => http.verify());

  function element(testId: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  async function settle(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
  }

  async function shown(
    state: OnboardingState,
    inputs: Record<string, unknown> = {},
  ): Promise<void> {
    Object.entries(inputs).forEach(([name, value]) => fixture.componentRef.setInput(name, value));
    fixture.detectChanges();
    http.expectOne({ method: 'GET', url }).flush(state);
    await settle();
  }

  it('ac9_4_greets_by_name_and_lists_the_steps_with_their_buttons', async () => {
    await shown(employeeState());

    expect(element('onboarding-checklist')?.querySelector('h2')?.textContent).toContain(
      'First steps',
    );
    expect(element('onboarding-checklist')?.textContent).toContain('Welcome, Olena!');
    expect(element('onboarding-progress')?.textContent).toContain('2 of 5 done');
    expect(element('onboarding-step-NOTICE')?.dataset['done']).toBe('true');
    expect(element('onboarding-open-NOTICE')).toBeNull();
    expect(element('onboarding-open-FIRST_AWARD')?.getAttribute('href')).toBe('/awards/new');
    expect(element('onboarding-open-VISIBILITY')?.getAttribute('href')).toBe('/privacy#choices');
    expect(element('onboarding-open-NOTIFICATIONS')?.getAttribute('href')).toBe(
      '/profile?section=notifications',
    );
  });

  it('ac11_2_shows_the_progress_ring_numbered_steps_and_the_next_step_first', async () => {
    await shown(employeeState());

    expect(element('onboarding-ring')?.textContent?.trim()).toBe('2/5');
    expect(element('onboarding-ring')?.getAttribute('aria-hidden')).toBe('true');
    expect(element('onboarding-ring-value')?.getAttribute('stroke-dasharray')).toMatch(
      /^125\.\d+ /,
    );
    expect(element('onboarding-step-NOTICE')?.textContent).toContain('Done');
    expect(element('onboarding-number-FIRST_AWARD')?.textContent?.trim()).toBe('3');
    expect(element('onboarding-step-FIRST_AWARD')?.dataset['next']).toBe('true');
    expect(element('onboarding-step-VISIBILITY')?.dataset['next']).toBeUndefined();
    expect(element('onboarding-open-FIRST_AWARD')?.classList).toContain(
      'mat-mdc-unelevated-button',
    );
    expect(element('onboarding-open-VISIBILITY')?.classList).not.toContain('mat-mdc-button-base');
  });

  it('ac11_2_draws_no_progress_before_the_first_step_is_done', async () => {
    const fresh = employeeState();
    fresh.steps = fresh.steps.map((step) => ({ ...step, done: false }));
    await shown(fresh);

    expect(element('onboarding-ring')?.textContent?.trim()).toBe('0/5');
    expect(element('onboarding-ring-value')).toBeNull();
    expect(element('onboarding-step-NOTICE')?.dataset['next']).toBe('true');
  });

  it('ac9_4_marks_a_stored_step_done_when_its_page_opens', async () => {
    await shown(employeeState());

    element('onboarding-open-VISIBILITY')?.click();
    const save = http.expectOne({ method: 'PUT', url });

    expect(save.request.body.done).toEqual(['PROFILE', 'VISIBILITY']);
    save.flush(employeeState());
  });

  it('ac9_4_hides_the_list_for_good', async () => {
    await shown(employeeState());

    element('onboarding-dismiss')?.click();
    const save = http.expectOne({ method: 'PUT', url });
    save.flush(employeeState({ dismissed: true }));
    await settle();

    expect(save.request.body.dismissed).toBe(true);
    expect(element('onboarding-checklist')).toBeNull();
  });

  it('ac9_4_shows_nothing_once_dismissed', async () => {
    await shown(employeeState({ dismissed: true }));

    expect(element('onboarding-checklist')).toBeNull();
  });

  it('ac9_4_says_once_that_everything_is_done_and_stores_it', async () => {
    const done = employeeState();
    done.steps = done.steps.map((step) => ({ ...step, done: true }));
    fixture.detectChanges();
    http.expectOne({ method: 'GET', url }).flush(done);
    await settle();
    const save = http.expectOne({ method: 'PUT', url });
    save.flush({ ...done, dismissed: true });
    await settle();

    expect(save.request.body.dismissed).toBe(true);
    expect(element('onboarding-all-done')?.textContent).toContain('All first steps are done');
    expect(element('onboarding-dismiss')).toBeNull();
  });

  it('ac9_5_offers_the_tour_on_my_dashboard_only', async () => {
    await shown(employeeState());

    expect(element('onboarding-tour-start')).toBeNull();
  });

  it('ac9_5_stores_the_tour_as_seen_when_it_ends', async () => {
    await shown(employeeState(), { tour: true });

    element('onboarding-tour-start')?.click();
    fixture.detectChanges();
    expect(document.querySelector('[data-testid="onboarding-tour"]')).not.toBeNull();
    document.querySelector<HTMLElement>('[data-testid="tour-skip"]')?.click();
    fixture.detectChanges();
    const save = http.expectOne({ method: 'PUT', url });

    expect(save.request.body.tourSeen).toBe(true);
    expect(document.querySelector('[data-testid="onboarding-tour"]')).toBeNull();
    save.flush(employeeState({ tourSeen: true }));
  });

  it('a5_stays_off_a_landing_page_for_an_account_with_my_dashboard', () => {
    fixture.componentRef.setInput('landing', true);
    fixture.detectChanges();

    http.expectNone(url);
    expect(element('onboarding-checklist')).toBeNull();
  });

  it('a5_shows_on_the_landing_page_of_an_account_without_my_dashboard', async () => {
    granted.set(['audit:read:all']);
    await shown(
      employeeState({
        steps: [
          { key: 'NOTICE', done: true, derived: true },
          { key: 'PROFILE', done: false, derived: false },
          { key: 'NOTIFICATIONS', done: false, derived: false },
        ],
      }),
      { landing: true },
    );

    expect(element('onboarding-checklist')?.querySelectorAll('li')).toHaveLength(3);
  });
});
