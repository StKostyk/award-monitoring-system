import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { LanguageService } from '../../../core/i18n/language.service';
import {
  LegalHoldDialogComponent,
  LegalHoldDialogData,
} from '../legal-hold-dialog/legal-hold-dialog.component';
import { LegalHold, LegalHoldOverview, LegalHoldsService } from '../legal-holds.service';
import { LegalHoldCardComponent } from './legal-hold-card.component';

const translations = {
  uk: {
    admin: {
      detail: {
        legalHold: {
          title: 'Юридичне утримання',
          none: 'Утримання немає',
          active: 'Діє юридичне утримання',
          placedBy: 'Встановлено {{at}}, {{name}}',
          liftedBy: 'Знято {{at}}, {{name}}',
          history: 'Попередні утримання',
          erasure: 'Видалення {{at}}',
          place: 'Встановити утримання',
          lift: 'Зняти утримання',
          placed: 'Утримання встановлено.',
          lifted: 'Утримання знято.',
          problems: { load: 'Не вдалося завантажити', network: 'Сервер недоступний' },
        },
      },
    },
  },
};

const OFFICER = { id: 2, name: 'Галина Захист', email: 'gdpr@chnu.edu.ua' };

const ACTIVE: LegalHold = {
  id: 6,
  reason: 'Inspection 2026/14 by the ministry',
  placedAt: '2026-10-16T10:00:00Z',
  placedBy: OFFICER,
  liftedAt: null,
  liftedBy: null,
  liftReason: null,
};

const LIFTED: LegalHold = {
  id: 4,
  reason: 'Court case 12/2025',
  placedAt: '2026-01-10T09:00:00Z',
  placedBy: OFFICER,
  liftedAt: '2026-03-01T09:00:00Z',
  liftedBy: OFFICER,
  liftReason: 'The case was closed by the court',
};

describe('LegalHoldCardComponent', () => {
  let fixture: ComponentFixture<LegalHoldCardComponent>;
  let api: {
    list: ReturnType<typeof vi.fn>;
    place: ReturnType<typeof vi.fn>;
    lift: ReturnType<typeof vi.fn>;
  };
  let dialog: { open: ReturnType<typeof vi.fn> };

  async function create(overview: LegalHoldOverview, erased = false): Promise<HTMLElement> {
    api = {
      list: vi.fn().mockReturnValue(of(overview)),
      place: vi.fn().mockReturnValue(of(ACTIVE)),
      lift: vi.fn().mockReturnValue(of(LIFTED)),
    };
    dialog = { open: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [
        LegalHoldCardComponent,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        { provide: LegalHoldsService, useValue: api },
        { provide: MatDialog, useValue: dialog },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(LegalHoldCardComponent);
    fixture.componentRef.setInput('userId', 7);
    fixture.componentRef.setInput('name', 'Анастасія Працівник');
    fixture.componentRef.setInput('erased', erased);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function find(element: HTMLElement, id: string): HTMLElement | null {
    return element.querySelector<HTMLElement>(`[data-testid="${id}"]`);
  }

  function lastDialogData(): LegalHoldDialogData {
    return dialog.open.mock.calls.at(-1)?.[1].data as LegalHoldDialogData;
  }

  it('ac3_6_shows_no_legal_hold_and_places_one_through_the_dialog', async () => {
    const element = await create({ holds: [], erasureScheduledFor: null });

    expect(api.list).toHaveBeenCalledWith(7);
    expect(find(element, 'legal-hold-none')?.textContent).toContain('Утримання немає');
    expect(find(element, 'legal-hold-lift')).toBeNull();

    api.list.mockReturnValue(of({ holds: [ACTIVE], erasureScheduledFor: null }));
    dialog.open.mockReturnValue({ afterClosed: () => of(ACTIVE) });
    find(element, 'legal-hold-place')?.click();
    fixture.detectChanges();

    expect(dialog.open.mock.calls[0][0]).toBe(LegalHoldDialogComponent);
    expect(lastDialogData()).toEqual(
      expect.objectContaining({ mode: 'place', name: 'Анастасія Працівник' }),
    );
    lastDialogData().submit('Inspection 2026/14 by the ministry').subscribe();
    expect(api.place).toHaveBeenCalledWith(7, 'Inspection 2026/14 by the ministry');
    expect(find(element, 'legal-hold-message')?.textContent).toContain('Утримання встановлено.');
    expect(find(element, 'legal-hold-active')?.textContent).toContain(
      'Inspection 2026/14 by the ministry',
    );
    expect(find(element, 'legal-hold-place')).toBeNull();
  });

  it('ac3_4_ac3_6_shows_the_active_hold_the_history_and_lifts_the_active_hold', async () => {
    const element = await create({
      holds: [ACTIVE, LIFTED],
      erasureScheduledFor: '2026-11-15T10:00:00Z',
    });

    expect(find(element, 'legal-hold-active')?.textContent).toContain('Галина Захист');
    expect(find(element, 'legal-hold-active')?.textContent).toContain('16.10.2026, 13:00');
    expect(find(element, 'legal-hold-erasure')?.textContent).toContain('15.11.2026');
    const history = find(element, 'legal-hold-history')?.textContent ?? '';
    expect(history).toContain('Court case 12/2025');
    expect(history).toContain('The case was closed by the court');
    expect(history).not.toContain('Inspection 2026/14');

    dialog.open.mockReturnValue({ afterClosed: () => of(LIFTED) });
    find(element, 'legal-hold-lift')?.click();
    fixture.detectChanges();

    expect(lastDialogData().mode).toBe('lift');
    lastDialogData().submit('Inspection closed without findings').subscribe();
    expect(api.lift).toHaveBeenCalledWith(7, 6, 'Inspection closed without findings');
    expect(find(element, 'legal-hold-message')?.textContent).toContain('Утримання знято.');
    expect(api.list).toHaveBeenCalledTimes(2);
  });

  it('ac3_6_reloads_without_a_message_after_a_stale_dialog_and_not_after_a_cancel', async () => {
    const element = await create({ holds: [], erasureScheduledFor: null });

    dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });
    find(element, 'legal-hold-place')?.click();

    expect(api.list).toHaveBeenCalledTimes(1);

    dialog.open.mockReturnValue({ afterClosed: () => of('stale') });
    find(element, 'legal-hold-place')?.click();
    fixture.detectChanges();

    expect(api.list).toHaveBeenCalledTimes(2);
    expect(find(element, 'legal-hold-message')).toBeNull();
  });

  it('ac3_2_offers_no_place_button_on_an_erased_account', async () => {
    const element = await create({ holds: [], erasureScheduledFor: null }, true);

    expect(find(element, 'legal-hold-none')).not.toBeNull();
    expect(find(element, 'legal-hold-place')).toBeNull();
  });

  it('ac3_6_reports_a_failed_load', async () => {
    const element = await create({ holds: [], erasureScheduledFor: null });
    api.list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    dialog.open.mockReturnValue({ afterClosed: () => of('stale') });

    find(element, 'legal-hold-place')?.click();
    fixture.detectChanges();

    expect(find(element, 'legal-hold-error')?.textContent).toContain('Не вдалося завантажити');
  });

  it('ac3_6_reports_an_unreachable_server', async () => {
    const element = await create({ holds: [], erasureScheduledFor: null });
    api.list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    dialog.open.mockReturnValue({ afterClosed: () => of('stale') });

    find(element, 'legal-hold-place')?.click();
    fixture.detectChanges();

    expect(find(element, 'legal-hold-error')?.textContent).toContain('Сервер недоступний');
  });
});
