import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Observable, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import {
  Award,
  AwardPage,
  AwardsService,
  NO_FILTERS,
} from '../../../features/awards/awards.service';
import { AuthService } from '../../auth/auth.service';
import { NO_PERMISSIONS, TokenPermissions, readPermissions } from '../../auth/permissions';
import { LanguageService } from '../../i18n/language.service';
import {
  CommandPaletteComponent,
  PALETTE_AWARD_LIMIT,
  PALETTE_DEBOUNCE_MS,
} from './command-palette.component';

const EMPLOYEE = ['award:read:own', 'award:create', 'analytics:read:own'];
const SECRETARY = [...EMPLOYEE, 'user:read:scope'];

function permissionsOf(permissions: string[], roleScopes: string[] = []): TokenPermissions {
  const claims = { permissions, role_scopes: roleScopes };
  return readPermissions(`header.${btoa(JSON.stringify(claims))}.signature`);
}

function award(id: number, titleUk: string): Award {
  return { id, title: null, titleUk } as Award;
}

function page(content: Award[]): AwardPage {
  return { content, totalElements: content.length } as AwardPage;
}

describe('CommandPaletteComponent', () => {
  const dialog = { close: vi.fn() };
  const router = { navigateByUrl: vi.fn().mockResolvedValue(true) };
  const awards = { list: vi.fn<(...args: unknown[]) => Observable<AwardPage>>() };
  const auth = {
    isAuthenticated: signal(true),
    permissions: signal<TokenPermissions>(NO_PERMISSIONS),
  };
  let fixture: ComponentFixture<CommandPaletteComponent>;

  const element = (): HTMLElement => fixture.nativeElement;
  const input = (): HTMLInputElement =>
    element().querySelector('[data-testid="palette-input"]') as HTMLInputElement;
  const options = (): HTMLElement[] => Array.from(element().querySelectorAll('[role="option"]'));
  const labels = (): string[] => options().map((option) => option.textContent?.trim() ?? '');

  function type(text: string): void {
    input().value = text;
    input().dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function key(name: string): void {
    input().dispatchEvent(new KeyboardEvent('keydown', { key: name, cancelable: true }));
    fixture.detectChanges();
  }

  beforeEach(async () => {
    vi.useFakeTimers();
    dialog.close.mockClear();
    router.navigateByUrl.mockClear();
    awards.list.mockReset();
    awards.list.mockReturnValue(of(page([])));
    auth.permissions.set(permissionsOf(EMPLOYEE));
    await TestBed.configureTestingModule({
      imports: [
        CommandPaletteComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            uk: {
              app: {
                home: 'Головна',
                palette: {
                  label: 'Пошук розділів і нагород',
                  sections: 'Розділи',
                  awards: 'Нагороди',
                  empty: 'Нічого не знайдено.',
                },
              },
              awards: { title: 'Мої нагороди' },
              analytics: { title: 'Моя аналітика' },
              achievements: { title: 'Досягнення' },
              reviews: { title: 'Розгляд' },
              admin: { users: { title: 'Користувачі' } },
            },
          },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        { provide: MatDialogRef, useValue: dialog },
        { provide: Router, useValue: router },
        { provide: AwardsService, useValue: awards },
        { provide: AuthService, useValue: auth },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CommandPaletteComponent);
    fixture.detectChanges();
  });

  afterEach(() => vi.useRealTimers());

  it('ac3_4_lists_the_sections_the_person_may_open', () => {
    expect(labels()).toEqual(['Головна', 'Мої нагороди', 'Моя аналітика', 'Досягнення']);
    expect(input().getAttribute('aria-activedescendant')).toBe('palette-section-home');
  });

  it('ac3_6_offers_no_review_or_administration_section_to_an_employee', () => {
    expect(labels()).not.toContain('Розгляд');
    expect(labels()).not.toContain('Користувачі');
  });

  it('ac3_6_offers_reviews_and_users_to_a_secretary', () => {
    auth.permissions.set(permissionsOf(SECRETARY, ['FACULTY_SECRETARY:9']));
    fixture.detectChanges();

    expect(labels()).toContain('Розгляд');
    expect(labels()).toContain('Користувачі');
  });

  it('ac3_5_filters_sections_by_name_from_two_characters', () => {
    type('н');
    expect(labels()).toHaveLength(4);

    type('НАГ');

    expect(labels()).toEqual(['Мої нагороди']);
  });

  it('ac9_2_finds_sections_from_text_typed_in_decomposed_form', () => {
    type('Мої'.normalize('NFD'));

    expect(labels()).toEqual(['Мої нагороди']);
  });

  it('ac3_5_asks_for_five_awards_250_ms_after_the_last_keystroke', () => {
    awards.list.mockReturnValue(of(page([award(7, 'Грамота МОН'), award(8, 'Подяка ректора')])));

    type('гр');
    vi.advanceTimersByTime(PALETTE_DEBOUNCE_MS - 1);
    expect(awards.list).not.toHaveBeenCalled();

    type('грам');
    vi.advanceTimersByTime(PALETTE_DEBOUNCE_MS);
    fixture.detectChanges();

    expect(awards.list).toHaveBeenCalledTimes(1);
    expect(awards.list).toHaveBeenCalledWith({ ...NO_FILTERS, q: 'грам' }, 0, PALETTE_AWARD_LIMIT);
    expect(labels()).toEqual(['Грамота МОН', 'Подяка ректора']);
    expect(element().textContent).toContain('Нагороди');
  });

  it('ac3_5_drops_a_slower_answer_when_the_next_keystroke_arrives', () => {
    const slow = new Subject<AwardPage>();
    awards.list.mockReturnValueOnce(slow).mockReturnValueOnce(of(page([award(2, 'Нова')])));

    type('ст');
    vi.advanceTimersByTime(PALETTE_DEBOUNCE_MS);
    type('нов');
    vi.advanceTimersByTime(PALETTE_DEBOUNCE_MS);
    slow.next(page([award(1, 'Стара')]));
    fixture.detectChanges();

    expect(labels()).toEqual(['Нова']);
    expect(slow.observed).toBe(false);
  });

  it('ac3_5_shows_sections_only_when_the_search_fails_and_recovers_on_the_next_keystroke', () => {
    awards.list
      .mockReturnValueOnce(
        throwError(() => new HttpErrorResponse({ status: HttpStatusCode.Unauthorized })),
      )
      .mockReturnValueOnce(of(page([award(3, 'Мої нагороди 2025')])));

    type('мої');
    vi.advanceTimersByTime(PALETTE_DEBOUNCE_MS);
    fixture.detectChanges();
    expect(labels()).toEqual(['Мої нагороди']);

    type('мої н');
    vi.advanceTimersByTime(PALETTE_DEBOUNCE_MS);
    fixture.detectChanges();

    expect(labels()).toEqual(['Мої нагороди', 'Мої нагороди 2025']);
  });

  it('ac3_5_moves_with_the_arrow_keys_and_opens_the_choice_on_enter', () => {
    key('ArrowDown');
    key('ArrowDown');
    expect(options()[2].getAttribute('aria-selected')).toBe('true');

    key('ArrowUp');
    key('Enter');

    expect(dialog.close).toHaveBeenCalled();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/awards');
  });

  it('ac3_5_wraps_the_selection_and_opens_an_award_on_click', () => {
    awards.list.mockReturnValue(of(page([award(7, 'Грамота')])));
    key('ArrowUp');
    expect(options().at(-1)?.getAttribute('aria-selected')).toBe('true');

    type('грамота');
    vi.advanceTimersByTime(PALETTE_DEBOUNCE_MS);
    fixture.detectChanges();
    (element().querySelector('[data-testid="palette-award-7"]') as HTMLElement).click();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/awards/7');
  });

  it('ac3_5_drops_the_awards_of_the_previous_text_at_the_next_keystroke', () => {
    awards.list.mockReturnValueOnce(of(page([award(7, 'Грамота МОН')])));
    type('грам');
    vi.advanceTimersByTime(PALETTE_DEBOUNCE_MS);
    fixture.detectChanges();
    expect(labels()).toEqual(['Грамота МОН']);

    type('грамx');
    key('Enter');

    expect(labels()).toEqual([]);
    expect(element().querySelector('[data-testid="palette-empty"]')).toBeNull();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('ac3_6_asks_for_no_awards_without_award_read_own', () => {
    auth.permissions.set(permissionsOf(['user:read:all']));
    fixture.detectChanges();

    type('кор');
    vi.advanceTimersByTime(PALETTE_DEBOUNCE_MS);
    fixture.detectChanges();

    expect(awards.list).not.toHaveBeenCalled();
    expect(labels()).toEqual(['Користувачі']);
  });

  it('ac3_5_says_when_nothing_matches', () => {
    type('zz');
    expect(element().querySelector('[data-testid="palette-empty"]')).toBeNull();
    vi.advanceTimersByTime(PALETTE_DEBOUNCE_MS);
    fixture.detectChanges();

    expect(options()).toHaveLength(0);
    expect(element().querySelector('[data-testid="palette-empty"]')?.textContent).toContain(
      'Нічого не знайдено.',
    );
    key('Enter');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });
});
