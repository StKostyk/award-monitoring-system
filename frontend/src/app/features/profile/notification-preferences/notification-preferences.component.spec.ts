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

  function box(fixture: { nativeElement: HTMLElement }, testId: string): HTMLInputElement {
    return fixture.nativeElement.querySelector(
      `[data-testid="${testId}"] input`,
    ) as HTMLInputElement;
  }

  afterEach(() => http.verify());

  it('ac3_6_lists_every_type_once_in_three_groups', async () => {
    const fixture = await loaded();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelectorAll('[data-testid="preferences-group"]').length).toBe(3);
    expect(PREFERENCE_GROUPS.flatMap((group) => group.types).sort()).toEqual(
      [...NOTIFICATION_TYPES].sort(),
    );
    NOTIFICATION_TYPES.forEach((type) => {
      expect(box(fixture, `preference-${type}-email`).checked).toBe(true);
      expect(box(fixture, `preference-${type}-inApp`).checked).toBe(true);
    });
    expect(element.querySelector('[data-testid="preferences-security"]')?.textContent).toContain(
      'надсилаються завжди',
    );
  });

  it('ac3_6_disables_the_last_ticked_box_of_a_type', async () => {
    const fixture = await loaded();

    box(fixture, 'preference-AWARD_APPROVED-email').click();
    fixture.detectChanges();

    const inApp = box(fixture, 'preference-AWARD_APPROVED-inApp');
    expect(inApp.checked).toBe(true);
    expect(inApp.disabled).toBe(true);
    expect(inApp.getAttribute('aria-describedby')).toContain('preferences-last-hint');
    expect(box(fixture, 'preference-AWARD_APPROVED-email').disabled).toBe(false);
    expect(box(fixture, 'preference-AWARD_REJECTED-inApp').disabled).toBe(false);
  });

  it('ac3_6_saves_with_one_put_and_confirms', async () => {
    const fixture = await loaded();
    const save = fixture.nativeElement.querySelector(
      '[data-testid="preferences-save"]',
    ) as HTMLButtonElement;
    expect(save.disabled).toBe(true);

    box(fixture, 'preference-REVIEW_OVERDUE-email').click();
    fixture.detectChanges();
    save.click();

    const request = http.expectOne(URL);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.preferences).toEqual([
      { type: 'REVIEW_OVERDUE', email: false, inApp: true },
    ]);
    const stored = allOn().map((preference) =>
      preference.type === 'REVIEW_OVERDUE' ? { ...preference, email: false } : preference,
    );
    request.flush({ preferences: stored });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="preferences-saved"]').textContent,
    ).toContain(uk.notifications.preferences.saved);
    expect(save.disabled).toBe(true);
  });

  it('ac1_sends_only_the_types_changed_since_the_last_load_or_save', async () => {
    const fixture = await loaded();
    const save = fixture.nativeElement.querySelector(
      '[data-testid="preferences-save"]',
    ) as HTMLButtonElement;

    box(fixture, 'preference-AWARD_APPROVED-inApp').click();
    box(fixture, 'preference-AWARD_RETURNED-email').click();
    box(fixture, 'preference-AWARD_RETURNED-email').click();
    fixture.detectChanges();
    save.click();

    const first = http.expectOne(URL);
    const sent = first.request.body.preferences as NotificationPreference[];
    expect(sent.map((preference) => preference.type)).toEqual(['AWARD_APPROVED']);
    const fromOtherTab = allOn().map((preference) => {
      if (preference.type === 'AWARD_APPROVED') {
        return { ...preference, inApp: false };
      }
      return preference.type === 'AWARD_RETURNED' ? { ...preference, email: false } : preference;
    });
    first.flush({ preferences: fromOtherTab });
    fixture.detectChanges();

    expect(box(fixture, 'preference-AWARD_RETURNED-email').checked).toBe(false);
    expect(box(fixture, 'preference-AWARD_APPROVED-inApp').checked).toBe(false);
    expect(box(fixture, 'preference-AWARD_APPROVED-email').checked).toBe(true);
    box(fixture, 'preference-ROLE_ASSIGNED-email').click();
    fixture.detectChanges();
    save.click();

    expect(http.expectOne(URL).request.body.preferences).toEqual([
      { type: 'ROLE_ASSIGNED', email: false, inApp: true },
    ]);
  });

  it('ac3_6_shows_a_failed_save_and_keeps_the_choice', async () => {
    const fixture = await loaded();
    box(fixture, 'preference-ROLE_ASSIGNED-inApp').click();
    fixture.detectChanges();
    (
      fixture.nativeElement.querySelector('[data-testid="preferences-save"]') as HTMLElement
    ).click();

    http
      .expectOne(URL)
      .flush(
        { type: 'https://chnu.edu.ua/problems/validation-failed' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="preferences-error"]').textContent,
    ).toContain(uk.notifications.preferences.errors.failed);
    expect(box(fixture, 'preference-ROLE_ASSIGNED-inApp').checked).toBe(false);
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
