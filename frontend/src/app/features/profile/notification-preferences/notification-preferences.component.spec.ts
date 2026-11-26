import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';

import en from '../../../../../public/i18n/en.json';
import uk from '../../../../../public/i18n/uk.json';
import { environment } from '../../../../environments/environment';
import {
  NOTIFICATION_TYPES,
  NotificationPreference,
} from '../../../core/notifications/notification.api';
import {
  NotificationPreferencesComponent,
  PREFERENCE_GROUPS,
} from './notification-preferences.component';

const URL = `${environment.apiUrl}/users/me/notification-preferences`;

function allOn(): NotificationPreference[] {
  return NOTIFICATION_TYPES.map((type) => ({ type, email: true, inApp: true }));
}

describe('NotificationPreferencesComponent', () => {
  let http: HttpTestingController;

  async function setup(fragment: string | null = null) {
    await TestBed.configureTestingModule({
      imports: [
        NotificationPreferencesComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: { uk },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { fragment } } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(NotificationPreferencesComponent);
    fixture.detectChanges();
    return fixture;
  }

  function loaded(preferences = allOn()) {
    return setup().then((fixture) => {
      http.expectOne(URL).flush({ preferences });
      fixture.detectChanges();
      return fixture;
    });
  }

  function toggle(fixture: { nativeElement: HTMLElement }, testId: string): HTMLButtonElement {
    return fixture.nativeElement.querySelector(
      `[data-testid="${testId}"] button[role="switch"]`,
    ) as HTMLButtonElement;
  }

  function on(fixture: { nativeElement: HTMLElement }, testId: string): boolean {
    return toggle(fixture, testId).getAttribute('aria-checked') === 'true';
  }

  function text(fixture: { nativeElement: HTMLElement }, testId: string): string | undefined {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`)?.textContent?.trim();
  }

  function stored(type: string, change: Partial<NotificationPreference>) {
    return allOn().map((preference) =>
      preference.type === type ? { ...preference, ...change } : preference,
    );
  }

  afterEach(() => http.verify());

  it('ac3_6_lists_every_type_once_in_three_groups_as_switches', async () => {
    const fixture = await loaded();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelectorAll('[data-testid="preferences-group"]').length).toBe(3);
    expect(PREFERENCE_GROUPS.flatMap((group) => group.types).sort()).toEqual(
      [...NOTIFICATION_TYPES].sort(),
    );
    NOTIFICATION_TYPES.forEach((type) => {
      expect(on(fixture, `preference-${type}-email`)).toBe(true);
      expect(on(fixture, `preference-${type}-inApp`)).toBe(true);
    });
    expect(toggle(fixture, 'preference-AWARD_APPROVED-email').getAttribute('aria-label')).toBe(
      'Е-пошта: Нагороду погоджено',
    );
    expect(element.querySelector('[data-testid="preferences-save"]')).toBeNull();
    expect(text(fixture, 'preferences-security')).toContain('надсилаються завжди');
  });

  it('ac6_3_saves_one_switch_with_one_put_and_confirms', async () => {
    const fixture = await loaded();

    toggle(fixture, 'preference-REVIEW_OVERDUE-email').click();
    fixture.detectChanges();

    const request = http.expectOne(URL);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.preferences).toEqual([
      { type: 'REVIEW_OVERDUE', email: false, inApp: true },
    ]);
    expect(toggle(fixture, 'preference-REVIEW_OVERDUE-inApp').disabled).toBe(true);
    expect(toggle(fixture, 'preference-AWARD_APPROVED-inApp').disabled).toBe(false);
    request.flush({ preferences: stored('REVIEW_OVERDUE', { email: false }) });
    fixture.detectChanges();

    expect(text(fixture, 'preferences-saved')).toContain(uk.notifications.preferences.saved);
    expect(on(fixture, 'preference-REVIEW_OVERDUE-email')).toBe(false);
    expect(toggle(fixture, 'preference-REVIEW_OVERDUE-inApp').disabled).toBe(false);
  });

  it('ac6_3_refuses_to_switch_off_the_last_channel_and_turns_it_back_on', async () => {
    const fixture = await loaded(stored('AWARD_APPROVED', { email: false }));

    toggle(fixture, 'preference-AWARD_APPROVED-inApp').click();
    fixture.detectChanges();

    http.expectNone(URL);
    expect(on(fixture, 'preference-AWARD_APPROVED-inApp')).toBe(true);
    expect(text(fixture, 'preferences-error')).toBe(uk.notifications.preferences.lastChannel);
    expect(
      toggle(fixture, 'preference-AWARD_APPROVED-inApp').getAttribute('aria-describedby'),
    ).toContain('preferences-last-hint');
  });

  it('ac6_3_takes_the_stored_choices_from_each_answer', async () => {
    const fixture = await loaded();

    toggle(fixture, 'preference-AWARD_APPROVED-inApp').click();
    fixture.detectChanges();
    const fromOtherTab = stored('AWARD_APPROVED', { inApp: false }).map((preference) =>
      preference.type === 'AWARD_RETURNED' ? { ...preference, email: false } : preference,
    );
    http.expectOne(URL).flush({ preferences: fromOtherTab });
    fixture.detectChanges();

    expect(on(fixture, 'preference-AWARD_RETURNED-email')).toBe(false);
    expect(on(fixture, 'preference-AWARD_APPROVED-inApp')).toBe(false);
    toggle(fixture, 'preference-ROLE_ASSIGNED-email').click();
    fixture.detectChanges();

    expect(http.expectOne(URL).request.body.preferences).toEqual([
      { type: 'ROLE_ASSIGNED', email: false, inApp: true },
    ]);
  });

  it('ac6_3_shows_a_failed_save_and_turns_the_switch_back', async () => {
    const fixture = await loaded();
    toggle(fixture, 'preference-ROLE_ASSIGNED-inApp').click();
    fixture.detectChanges();

    http
      .expectOne(URL)
      .flush(
        { type: 'https://chnu.edu.ua/problems/validation-failed' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    fixture.detectChanges();

    expect(text(fixture, 'preferences-error')).toContain(
      uk.notifications.preferences.errors.failed,
    );
    expect(on(fixture, 'preference-ROLE_ASSIGNED-inApp')).toBe(true);
    expect(toggle(fixture, 'preference-ROLE_ASSIGNED-inApp').disabled).toBe(false);
  });

  it('shows a load failure with a retry', async () => {
    const fixture = await setup();
    http.expectOne(URL).flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="preferences-load-error"]'),
    ).not.toBeNull();
    (
      fixture.nativeElement.querySelector('[data-testid="preferences-retry"]') as HTMLElement
    ).click();
    http.expectOne(URL).flush({ preferences: allOn() });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="preferences-load-error"]'),
    ).toBeNull();
  });

  it('has the same preference keys in both languages', () => {
    expect(Object.keys(uk.notifications.preferences.types).sort()).toEqual(
      [...NOTIFICATION_TYPES].sort(),
    );
    expect(Object.keys(en.notifications.preferences.types).sort()).toEqual(
      [...NOTIFICATION_TYPES].sort(),
    );
    expect(Object.keys(en.notifications.preferences.groups)).toEqual(
      PREFERENCE_GROUPS.map((group) => group.key),
    );
    expect(Object.keys(uk.notifications.preferences.groups)).toEqual(
      PREFERENCE_GROUPS.map((group) => group.key),
    );
  });
});
