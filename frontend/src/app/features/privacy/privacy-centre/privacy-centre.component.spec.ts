import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { environment } from '../../../../environments/environment';
import { LanguageService } from '../../../core/i18n/language.service';
import { ConsentOverview, ConsentsService } from '../consents.service';
import {
  ActivityEntry,
  ErasureRequest,
  PrivacyCentreService,
  PrivacySummary,
} from '../privacy-centre.service';
import { PrivacyCentreComponent, fileName } from './privacy-centre.component';

const OVERVIEW: ConsentOverview = {
  notice: {
    currentVersion: '1.0',
    acknowledgedVersion: '1.0',
    acknowledgedAt: '2026-10-15T08:00:00Z',
    acknowledgementRequired: false,
  },
  consents: [
    { type: 'DATA_PROCESSING', granted: true, changedAt: '2026-10-15T08:00:00Z' },
    { type: 'ANALYTICS', granted: false, changedAt: null },
  ],
  history: [{ type: 'DATA_PROCESSING', version: '1.0', granted: true, at: '2026-10-15T08:00:00Z' }],
};

const GRANTED: ConsentOverview = {
  ...OVERVIEW,
  consents: [
    OVERVIEW.consents[0],
    { type: 'ANALYTICS', granted: true, changedAt: '2026-10-15T09:00:00Z' },
  ],
  history: [
    ...OVERVIEW.history,
    { type: 'ANALYTICS', version: '1.0', granted: true, at: '2026-10-15T09:00:00Z' },
  ],
};

const SUMMARY: PrivacySummary = {
  awardsByVisibility: { PRIVATE: 2, UNIVERSITY: 1, PUBLIC: 0 },
  documentsShown: 3,
  emailOffTypes: ['AWARD_APPROVED', 'ROLE_ASSIGNED'],
  lastExportAt: null,
  erasure: null,
};

const PENDING: ErasureRequest = {
  id: 7,
  requestedAt: '2026-10-16T10:00:00Z',
  scheduledFor: '2026-11-15T10:00:00Z',
  postponed: false,
};

function entry(action: string, byMe: boolean, minute: number): ActivityEntry {
  return {
    action,
    occurredAt: `2026-10-15T09:${String(minute).padStart(2, '0')}:00Z`,
    entityType: 'USER',
    entityId: 5,
    byMe,
  };
}

describe('PrivacyCentreComponent', () => {
  let http: HttpTestingController;
  const consents = { overview: vi.fn(), choose: vi.fn() };
  const summaryUrl = `${environment.apiUrl}/users/me/privacy`;
  const activityUrl = `${environment.apiUrl}/users/me/activity?page=0&size=20`;
  const exportUrl = `${environment.apiUrl}/users/me/export`;

  async function configure() {
    await TestBed.configureTestingModule({
      imports: [
        PrivacyCentreComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: {
            uk: {
              privacy: {
                activity: { actions: { LOGIN_SUCCESS: 'Вхід' } },
                erasure: { scheduled: 'Буде видалено {{date}}.' },
              },
            },
          },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ConsentsService, useValue: consents },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  }

  async function setup(activity: ActivityEntry[] = [], totalPages = 1, summary = SUMMARY) {
    consents.overview.mockResolvedValue(OVERVIEW);
    await configure();
    const fixture = TestBed.createComponent(PrivacyCentreComponent);
    fixture.detectChanges();
    http.expectOne(summaryUrl).flush(summary);
    http.expectOne(activityUrl).flush({ content: activity, totalPages });
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  function query(fixture: { nativeElement: HTMLElement }, testId: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  it('ac2_3_shows_the_cards_in_order_with_the_notice_and_the_choices', async () => {
    const fixture = await setup();
    const cards = Array.from(
      fixture.nativeElement.querySelectorAll('mat-card') as NodeListOf<HTMLElement>,
    ).map((card) => card.dataset['testid']);

    expect(cards).toEqual([
      'privacy-notice-card',
      'privacy-consents-card',
      'privacy-choices-card',
      'privacy-data-card',
      'privacy-activity-card',
      'privacy-erasure-card',
    ]);
    expect(query(fixture, 'privacy-notice-acknowledged')?.textContent).toContain('15.10.2026');
    expect(query(fixture, 'privacy-awards-PRIVATE')?.textContent?.trim()).toBe('2');
    expect(query(fixture, 'privacy-documents')?.textContent?.trim()).toBe('3');
    expect(query(fixture, 'privacy-email-off')?.textContent?.trim()).toBe('2');
    expect(query(fixture, 'privacy-notifications-link')?.getAttribute('href')).toBe(
      '/profile#notifications',
    );
    expect(query(fixture, 'privacy-awards-link')?.getAttribute('href')).toBe('/awards');
  });

  it('ac2_4_stores_the_analytics_choice_and_shows_the_new_history_line', async () => {
    const fixture = await setup();
    consents.choose.mockReturnValue({
      subscribe: (observer: { next: (overview: ConsentOverview) => void }) =>
        observer.next(GRANTED),
    });

    fixture.componentInstance.chooseAnalytics({
      checked: true,
      source: { checked: true },
    } as never);
    fixture.detectChanges();

    expect(consents.choose).toHaveBeenCalledWith('ANALYTICS', true);
    expect(fixture.componentInstance.analytics()).toBe(true);
    expect(fixture.componentInstance.history()[0].type).toBe('ANALYTICS');
    expect(fixture.componentInstance.savingConsent()).toBe(false);
  });

  it('ac2_4_reverts_the_switch_with_an_error_line_when_the_choice_fails', async () => {
    const fixture = await setup();
    consents.choose.mockReturnValue({
      subscribe: (observer: { error: (error: unknown) => void }) => observer.error(new Error()),
    });
    const source = { checked: true };

    fixture.componentInstance.chooseAnalytics({ checked: true, source } as never);
    fixture.detectChanges();

    expect(source.checked).toBe(false);
    expect(fixture.componentInstance.analytics()).toBe(false);
    expect(query(fixture, 'privacy-analytics-error')).not.toBeNull();
  });

  it('ac2_5_downloads_the_export_and_refreshes_the_last_export_time', async () => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:export');
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined);
    const fixture = await setup();
    const button = (): HTMLButtonElement => query(fixture, 'privacy-download') as HTMLButtonElement;

    button().click();
    fixture.detectChanges();
    expect(button().disabled).toBe(true);
    const request = http.expectOne(exportUrl);
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['{}']), {
      headers: {
        'Content-Disposition': 'attachment; filename="award-monitoring-export-2026-10-15.json"',
      },
    });
    http.expectOne(summaryUrl).flush({ ...SUMMARY, lastExportAt: '2026-10-15T09:30:00Z' });
    http.expectOne(activityUrl).flush({ content: [entry('DATA_EXPORT', true, 30)], totalPages: 1 });
    fixture.detectChanges();

    expect((click.mock.contexts[0] as HTMLAnchorElement).download).toBe(
      'award-monitoring-export-2026-10-15.json',
    );
    expect(button().disabled).toBe(false);
    expect(fixture.componentInstance.exportMessage()).toBe('privacy.centre.data.downloaded');
    expect(query(fixture, 'privacy-last-export')).not.toBeNull();
  });

  it('ac2_5_shows_too_many_requests_on_429_and_a_general_failure_otherwise', async () => {
    const fixture = await setup();

    fixture.componentInstance.downloadData();
    http
      .expectOne(exportUrl)
      .flush(new Blob(['{}']), { status: 429, statusText: 'Too Many Requests' });
    expect(fixture.componentInstance.exportProblem()).toBe('privacy.centre.data.tooMany');

    fixture.componentInstance.downloadData();
    http.expectOne(exportUrl).flush(new Blob(['{}']), { status: 500, statusText: 'Server Error' });
    expect(fixture.componentInstance.exportProblem()).toBe('privacy.centre.data.failed');
    expect(fixture.componentInstance.exporting()).toBe(false);
  });

  it('ac2_5_falls_back_to_a_default_name_without_an_attachment_header', () => {
    expect(fileName(null)).toBe('award-monitoring-export.json');
    expect(fileName('attachment; filename=data.json')).toBe('data.json');
  });

  it('ac2_6_labels_known_actions_shows_unknown_codes_and_who_acted', async () => {
    const fixture = await setup([
      entry('LOGIN_SUCCESS', true, 2),
      entry('SOMETHING_NEW', false, 1),
    ]);
    const rows = fixture.nativeElement.querySelectorAll(
      '[data-testid="privacy-activity-row"]',
    ) as NodeListOf<HTMLElement>;

    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Вхід');
    expect(rows[0].textContent).toContain('privacy.activity.byMe');
    expect(rows[1].textContent).toContain('SOMETHING_NEW');
    expect(rows[1].textContent).toContain('privacy.activity.aboutMe');
    expect(rows[0].querySelector('time')?.textContent).toContain('15.10.2026');
  });

  it('ac2_6_shows_an_empty_state_and_loads_more_pages', async () => {
    const empty = await setup();
    expect(query(empty, 'privacy-activity-empty')).not.toBeNull();
    TestBed.resetTestingModule();

    const fixture = await setup([entry('LOGOUT', true, 5)], 2);
    query(fixture, 'privacy-activity-more')?.click();
    http
      .expectOne(`${environment.apiUrl}/users/me/activity?page=1&size=20`)
      .flush({ content: [entry('LOGIN_SUCCESS', true, 1)], totalPages: 2 });
    fixture.detectChanges();

    expect(fixture.componentInstance.activity().length).toBe(2);
    expect(query(fixture, 'privacy-activity-more')).toBeNull();
  });

  it('ac2_3_shows_a_load_error_when_the_summary_fails', async () => {
    consents.overview.mockRejectedValue(new Error());
    await configure();
    const fixture = TestBed.createComponent(PrivacyCentreComponent);
    fixture.detectChanges();
    http.expectOne(summaryUrl).flush(null, { status: 500, statusText: 'Server Error' });
    http.expectOne(activityUrl).flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(query(fixture, 'privacy-summary-error')).not.toBeNull();
    expect(query(fixture, 'privacy-activity-error')).not.toBeNull();
    expect(fixture.componentInstance.overviewFailed()).toBe(true);
  });

  describe('erasure', () => {
    const requestUrl = `${environment.apiUrl}/users/me/erasure-request`;
    const cancelUrl = `${requestUrl}/cancel`;

    function openDialog(result: ErasureRequest | undefined) {
      const open = vi.fn().mockReturnValue({ afterClosed: () => of(result) });
      TestBed.inject(MatDialog).open = open;
      return open;
    }

    it('ac4_7_explains_what_is_erased_and_kept_and_offers_the_request', async () => {
      const fixture = await setup();

      expect(query(fixture, 'privacy-erasure-terms')?.querySelectorAll('li').length).toBe(4);
      expect(query(fixture, 'privacy-erasure-open')).not.toBeNull();
      expect(query(fixture, 'privacy-erasure-scheduled')).toBeNull();
    });

    it('ac4_7_shows_the_date_and_the_cancel_button_after_a_request', async () => {
      const fixture = await setup();
      const open = openDialog(PENDING);
      TestBed.inject(PrivacyCentreService).requestErasure('Secret#2026').subscribe();
      http.expectOne(requestUrl).flush(PENDING);

      query(fixture, 'privacy-erasure-open')?.click();
      http.expectOne(activityUrl).flush({ content: [], totalPages: 1 });
      fixture.detectChanges();

      expect(open).toHaveBeenCalled();
      expect(query(fixture, 'privacy-erasure-scheduled')?.textContent).toContain('15.11.2026');
      expect(query(fixture, 'privacy-erasure-done')).not.toBeNull();
      expect(query(fixture, 'privacy-erasure-cancel')).not.toBeNull();
      expect(query(fixture, 'privacy-erasure-open')).toBeNull();
    });

    it('ac4_7_reloads_the_summary_when_the_dialog_closes_without_a_request', async () => {
      const fixture = await setup();
      openDialog(undefined);

      query(fixture, 'privacy-erasure-open')?.click();
      http.expectOne(summaryUrl).flush({ ...SUMMARY, erasure: PENDING });
      fixture.detectChanges();

      expect(query(fixture, 'privacy-erasure-scheduled')).not.toBeNull();
    });

    it('ac4_4_cancels_the_pending_request', async () => {
      const fixture = await setup([], 1, { ...SUMMARY, erasure: PENDING });
      expect(query(fixture, 'privacy-erasure-scheduled')?.textContent).toContain('15.11.2026');

      query(fixture, 'privacy-erasure-cancel')?.click();
      const cancel = http.expectOne(cancelUrl);
      expect(cancel.request.method).toBe('POST');
      cancel.flush(null, { status: 204, statusText: 'No Content' });
      http.expectOne(activityUrl).flush({ content: [], totalPages: 1 });
      fixture.detectChanges();

      expect(query(fixture, 'privacy-erasure-scheduled')).toBeNull();
      expect(query(fixture, 'privacy-erasure-done')).not.toBeNull();
      expect(query(fixture, 'privacy-erasure-open')).not.toBeNull();
    });

    it('ac4_4_reloads_when_nothing_is_pending_any_more', async () => {
      const fixture = await setup([], 1, { ...SUMMARY, erasure: PENDING });

      query(fixture, 'privacy-erasure-cancel')?.click();
      http
        .expectOne(cancelUrl)
        .flush(
          { type: 'urn:awards:problem:erasure-not-pending' },
          { status: 409, statusText: 'Conflict' },
        );
      http.expectOne(summaryUrl).flush(SUMMARY);
      fixture.detectChanges();

      expect(query(fixture, 'privacy-erasure-error')).not.toBeNull();
      expect(query(fixture, 'privacy-erasure-open')).not.toBeNull();
    });

    it('ac4_4_keeps_the_request_when_the_cancel_fails', async () => {
      const fixture = await setup([], 1, { ...SUMMARY, erasure: PENDING });

      query(fixture, 'privacy-erasure-cancel')?.click();
      http.expectOne(cancelUrl).flush(null, { status: 500, statusText: 'Error' });
      fixture.detectChanges();

      expect(query(fixture, 'privacy-erasure-error')).not.toBeNull();
      expect(query(fixture, 'privacy-erasure-scheduled')).not.toBeNull();
      expect(fixture.componentInstance.cancellingErasure()).toBe(false);
    });

    it('ac4_7_labels_the_erasure_actions', async () => {
      const fixture = await setup();

      expect(fixture.componentInstance.actionKey('ERASURE_REQUESTED')).toBe(
        'privacy.activity.actions.ERASURE_REQUESTED',
      );
      expect(fixture.componentInstance.actionKey('ERASURE_CANCELLED')).toBe(
        'privacy.activity.actions.ERASURE_CANCELLED',
      );
    });
  });
});
