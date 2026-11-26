import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { vi } from 'vitest';

import { languageStub, translations } from '../../analytics/analytics.testing';
import { ReminderDialogComponent, ReminderDialogData } from './reminder-dialog.component';

const DATA: ReminderDialogData = {
  unit: { id: 44, name: 'Department of Geometry', nameUk: null, code: null, type: 'DEPARTMENT' },
  year: 2026,
};

describe('ReminderDialogComponent', () => {
  let fixture: ComponentFixture<ReminderDialogComponent>;
  let http: HttpTestingController;
  const close = vi.fn();

  beforeEach(async () => {
    close.mockClear();
    await TestBed.configureTestingModule({
      imports: [ReminderDialogComponent, NoopAnimationsModule, translations('en')],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        languageStub('en').provider,
        { provide: MAT_DIALOG_DATA, useValue: DATA },
        { provide: MatDialogRef, useValue: { close } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ReminderDialogComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function element(testId: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  function members() {
    http
      .expectOne((r) => r.url.endsWith('/users') && r.params.get('organization') === '44')
      .flush({ content: [{ id: 7, firstName: 'Ivan', lastName: 'Bondar' }] });
    fixture.detectChanges();
  }

  function choose(id: number) {
    (
      fixture.componentInstance as unknown as { recipient: { setValue(v: number): void } }
    ).recipient.setValue(id);
  }

  function send() {
    element('reminder-send')!.click();
    return http.expectOne((r) => r.url.endsWith('/analytics/units/44/reminders'));
  }

  it('ac5_3_sends_the_reminder_to_the_chosen_colleague_and_closes_with_the_time', () => {
    members();
    expect(fixture.nativeElement.textContent).toContain('Department of Geometry: no award in 2026');
    choose(7);

    const request = send();
    expect(request.request.body).toEqual({ recipientId: 7 });
    request.flush(
      { unit: DATA.unit, recipient: { id: 7 }, sentAt: '2026-11-18T10:00:00Z' },
      { status: 202, statusText: 'Accepted' },
    );

    expect(close).toHaveBeenCalledWith({ sentAt: '2026-11-18T10:00:00Z' });
  });

  it('ac5_3_sends_nothing_without_a_colleague', () => {
    members();

    element('reminder-send')!.click();

    http.expectNone((r) => r.url.endsWith('/reminders'));
    expect(close).not.toHaveBeenCalled();
  });

  it('ac5_4_closes_with_the_last_reminder_when_somebody_reminded_first', () => {
    members();
    choose(7);

    send().flush(
      {
        type: 'urn:awards:problem:recently-reminded',
        status: 409,
        lastSentAt: '2026-11-17T08:00:00Z',
      },
      { status: 409, statusText: 'Conflict' },
    );

    expect(close).toHaveBeenCalledWith({ sentAt: '2026-11-17T08:00:00Z' });
  });

  it('ac5_5_explains_has_awards_invalid_recipient_and_a_lost_scope', () => {
    members();
    choose(7);
    const cases: [string, number, string][] = [
      ['has-awards', 409, 'already has an award'],
      ['invalid-recipient', 422, 'not an active member'],
      ['access-denied', 403, 'no longer remind'],
    ];

    for (const [type, status, text] of cases) {
      send().flush(
        { type: `urn:awards:problem:${type}`, status },
        { status, statusText: 'Refused' },
      );
      fixture.detectChanges();
      expect(element('reminder-problem')?.textContent).toContain(text);
    }
    expect(close).not.toHaveBeenCalled();
  });

  it('ac5_3_says_so_when_the_department_has_nobody_active', () => {
    http.expectOne((r) => r.url.endsWith('/users')).flush({ content: [] });
    fixture.detectChanges();

    expect(element('reminder-nobody')).not.toBeNull();
    expect((element('reminder-send') as HTMLButtonElement).disabled).toBe(true);
  });
});
