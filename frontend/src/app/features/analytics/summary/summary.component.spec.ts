import { Clipboard } from '@angular/cdk/clipboard';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, convertToParamMap, provideRouter } from '@angular/router';
import { Store, provideState, provideStore } from '@ngrx/store';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { AppLanguage } from '../../../core/i18n/language.service';
import { Page } from '../../awards/awards.service';
import {
  Achievement,
  AchievementsService,
  NO_ACHIEVEMENT_FILTERS,
} from '../../achievements/achievements.service';
import { currentKyivYear } from '../../../shared/date-format';
import { languageStub, sampleAnalytics, translations } from '../analytics.testing';
import { AnalyticsActions } from '../store/analytics.actions';
import { analyticsFeature } from '../store/analytics.feature';
import { SummaryComponent } from './summary.component';

const PERSON_ID = 12;

function page(totalElements: number): Page<Achievement> {
  return { content: [], totalElements, totalPages: 1, size: 1, number: 0 };
}

describe('SummaryComponent', () => {
  let fixture: ComponentFixture<SummaryComponent>;
  let store: Store;
  let dispatch: ReturnType<typeof vi.spyOn>;
  const queryParams = new BehaviorSubject<ParamMap>(convertToParamMap({}));
  const achievements = { list: vi.fn() };
  const clipboard = { copy: vi.fn() };

  async function open(
    params: Record<string, string> = { from: '2023', to: '2026' },
    language: AppLanguage = 'en',
  ): Promise<void> {
    queryParams.next(convertToParamMap(params));
    await TestBed.configureTestingModule({
      imports: [SummaryComponent, translations(language)],
      providers: [
        provideRouter([]),
        provideStore({}),
        provideState(analyticsFeature),
        languageStub(language).provider,
        { provide: ActivatedRoute, useValue: { queryParamMap: queryParams } },
        { provide: AchievementsService, useValue: achievements },
        { provide: Clipboard, useValue: clipboard },
        {
          provide: AuthService,
          useValue: {
            fullName: signal('Olena Petrenko'),
            profile: signal({
              id: PERSON_ID,
              organization: {
                id: 41,
                name: 'Department of Algebra and Informatics',
                nameUk: 'Кафедра алгебри та інформатики',
                type: 'DEPARTMENT',
              },
            }),
          },
        },
      ],
    }).compileComponents();
    store = TestBed.inject(Store);
    dispatch = vi.spyOn(store, 'dispatch');
    fixture = TestBed.createComponent(SummaryComponent);
    fixture.detectChanges();
  }

  function load(analytics = sampleAnalytics()): void {
    store.dispatch(AnalyticsActions.analyticsLoaded({ analytics }));
    fixture.detectChanges();
  }

  function element(testId: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  function text(testId: string): string {
    return element(testId)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  beforeEach(() => {
    achievements.list.mockReset().mockReturnValue(of(page(1)));
    clipboard.copy.mockReset().mockReturnValue(true);
  });

  afterEach(() => vi.unstubAllGlobals());

  it('ac3_1_loads_the_period_of_the_address_bar', async () => {
    await open();

    expect(dispatch).toHaveBeenCalledWith(
      AnalyticsActions.periodSelected({ period: { from: 2023, to: 2026 } }),
    );
  });

  it('ac3_1_shows_the_person_the_period_the_totals_and_the_levels', async () => {
    await open();
    load();

    expect(text('summary-name')).toBe('Olena Petrenko');
    expect(text('summary-department')).toBe('Department of Algebra and Informatics');
    expect(text('summary-period')).toContain('2023–2026');
    expect(text('summary-approved')).toContain('3');
    expect(text('summary-verified')).toContain('2');
    expect(text('summary-level-NATIONAL')).toContain('1');
    expect(element('summary-produced')).not.toBeNull();
  });

  it('ac3_1_lists_the_counted_awards_with_the_title_in_the_interface_language', async () => {
    await open();
    load();

    const rows = fixture.nativeElement.querySelectorAll('[data-testid="summary-award"]');
    expect(rows).toHaveLength(3);
    expect(rows[0].textContent).toContain('National grant');
    expect(rows[0].textContent).toContain('Ministry of Education');
    expect(rows[0].querySelector('[data-testid="summary-verified-badge"]')).not.toBeNull();
    expect(rows[1].textContent).toContain('Подяка кафедри');
  });

  it('ac3_1_the_ukrainian_summary_names_the_department_in_ukrainian', async () => {
    await open(undefined, 'uk');
    load();

    expect(text('summary-department')).toBe('Кафедра алгебри та інформатики');
    expect(
      fixture.nativeElement.querySelector('[data-testid="summary-award"]').textContent,
    ).toContain('Національний грант');
  });

  it('ac3_1_a_period_that_is_not_valid_is_reported_with_a_way_back', async () => {
    await open({ from: '2026', to: '2020' });

    expect(element('summary-invalid')).not.toBeNull();
    expect(element('summary-back')?.getAttribute('href')).toBe('/dashboard');
    expect(dispatch).not.toHaveBeenCalledWith(
      expect.objectContaining({ type: AnalyticsActions.periodSelected.type }),
    );
  });

  it('ac3_1_a_year_that_is_no_number_is_reported', async () => {
    await open({ from: 'abc' });

    expect(element('summary-invalid')).not.toBeNull();
  });

  it('ac4_1_a_year_after_the_current_kyiv_year_is_reported', async () => {
    await open({ to: String(currentKyivYear() + 1) });

    expect(text('summary-invalid')).toContain('The period is not valid.');
    expect(element('summary-error')).toBeNull();
  });

  it('ac3_2_print_opens_the_browser_print', async () => {
    const print = vi.fn();
    vi.stubGlobal('print', print);
    await open();
    load();

    element('summary-print')?.click();

    expect(print).toHaveBeenCalled();
  });

  it('ac4_4_print_waits_for_the_figures_of_a_new_period', async () => {
    await open();
    load();
    queryParams.next(convertToParamMap({ from: '2024', to: '2025' }));
    fixture.detectChanges();

    expect((element('summary-print') as HTMLButtonElement).disabled).toBe(true);

    load();

    expect((element('summary-print') as HTMLButtonElement).disabled).toBe(false);

    store.dispatch(AnalyticsActions.analyticsLoadFailed({ problem: 'unknown' }));
    fixture.detectChanges();

    expect((element('summary-print') as HTMLButtonElement).disabled).toBe(true);
  });

  it('ac3_4_copies_the_link_to_the_public_achievements_and_confirms', async () => {
    await open();
    load();

    expect(achievements.list).toHaveBeenCalledWith(
      { filters: { ...NO_ACHIEVEMENT_FILTERS, person: PERSON_ID }, page: 0, size: 1 },
      'public',
    );
    element('summary-copy-link')?.click();
    fixture.detectChanges();

    expect(clipboard.copy).toHaveBeenCalledWith(
      `${location.origin}/public/achievements?person=${PERSON_ID}`,
    );
    expect(text('summary-copied')).toContain('copied');
  });

  it('ac3_4_without_a_public_award_explains_why_there_is_no_link', async () => {
    achievements.list.mockReturnValue(of(page(0)));
    await open();
    load();

    expect(element('summary-copy-link')).toBeNull();
    expect(element('summary-public-hint')).not.toBeNull();
  });

  it('ac4_3_a_failed_public_check_is_reported_instead_of_the_hint', async () => {
    achievements.list.mockReturnValue(throwError(() => new Error('offline')));
    await open();
    load();

    expect(element('summary-copy-link')).toBeNull();
    expect(element('summary-public-hint')).toBeNull();
    expect(text('summary-public-failed')).toBe(
      'It could not be checked whether your awards are public. Reload the page to try again.',
    );
  });
});
