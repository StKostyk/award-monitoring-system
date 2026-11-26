import { TestBed } from '@angular/core/testing';

import { languageStub, translations } from '../../analytics.testing';
import { UnitHighlight } from '../unit-analytics.service';
import { ALGEBRA, ANALYSIS, facultyAnalytics, ref } from '../unit-analytics.testing';
import { HighlightsComponent } from './highlights.component';

describe('HighlightsComponent', () => {
  function render(
    highlights: UnitHighlight[],
    language: 'en' | 'uk' = 'en',
    inputs: Record<string, unknown> = {},
  ) {
    TestBed.configureTestingModule({
      imports: [translations(language)],
      providers: [languageStub(language).provider],
    });
    const fixture = TestBed.createComponent(HighlightsComponent);
    fixture.componentRef.setInput('highlights', highlights);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    fixture.detectChanges();
    return fixture;
  }

  function read(root: Element): string[] {
    return [...root.querySelectorAll('[data-testid="highlight"]')].map((item) =>
      ['count', 'previous', 'text']
        .map((part) => item.querySelector(`.highlight__${part}`)!.textContent!.trim())
        .join(' | '),
    );
  }

  it('ac2_6_shows_each_highlight_with_its_count_the_previous_one_and_a_sentence', () => {
    const element: HTMLElement = render([
      ...facultyAnalytics().highlights,
      { type: 'NO_AWARDS', unit: ref(ALGEBRA), current: 0, previous: 2 },
      { type: 'BELOW_AVERAGE', unit: ref(ANALYSIS), current: 1, previous: 1 },
    ]).nativeElement;
    const strengths = element.querySelector('[data-testid="highlights-strengths"]')!;
    const gaps = element.querySelector('[data-testid="highlights-gaps"]')!;

    expect(read(strengths)).toEqual([
      '11 | was 5 | Department of Algebra and Informatics: more awards than in the previous period',
    ]);
    expect(read(gaps)).toHaveLength(3);
    expect(read(gaps)[0]).toBe(
      '3 | was 8 | Department of Mathematical Analysis: fewer awards than in the previous period',
    );
    expect(gaps.textContent).toContain('no awards in this period');
    expect(gaps.textContent).toContain("under half of the faculty's average per member");
  });

  it('ac2_6_says_no_awards_in_either_period_without_a_previous_count_of_zero', () => {
    const element: HTMLElement = render([
      { type: 'NO_AWARDS', unit: ref(ALGEBRA), current: 0, previous: 0 },
    ]).nativeElement;

    expect(element.textContent).toContain(
      'Department of Algebra and Informatics: no awards in this period or the previous one',
    );
  });

  it('ac2_6_names_the_department_in_ukrainian', () => {
    const element: HTMLElement = render(facultyAnalytics().highlights, 'uk').nativeElement;

    expect(read(element)).toContain(
      '3 | було 8 | Кафедра математичного аналізу: менше нагород, ніж у попередньому періоді',
    );
  });

  it('ac2_6_says_when_nothing_stands_out', () => {
    const element: HTMLElement = render([]).nativeElement;

    expect(element.querySelector('[data-testid="highlights-none"]')!.textContent).toContain(
      'Nothing stands out in this period.',
    );
  });

  it('ac2_6_university_counts_above_average_among_the_strengths_with_its_own_sentences', () => {
    const element: HTMLElement = render(
      [
        { type: 'ABOVE_AVERAGE', unit: ref(ALGEBRA), current: 40, previous: 20 },
        { type: 'BELOW_AVERAGE', unit: ref(ANALYSIS), current: 1, previous: 1 },
      ],
      'en',
      { sentenceKeys: 'universityAnalytics.highlights' },
    ).nativeElement;

    expect(read(element.querySelector('[data-testid="highlights-strengths"]')!)).toEqual([
      "40 | was 20 | Department of Algebra and Informatics: at least one and a half times the university's average per member",
    ]);
    expect(element.querySelector('[data-testid="highlights-gaps"]')!.textContent).toContain(
      "under half of the university's average per member",
    );
  });

  it('ac4_5_caps_the_list_and_expands_it_in_place', () => {
    const highlights: UnitHighlight[] = [
      ...facultyAnalytics().highlights,
      { type: 'NO_AWARDS', unit: ref(ALGEBRA), current: 0, previous: 2 },
      { type: 'BELOW_AVERAGE', unit: ref(ANALYSIS), current: 1, previous: 1 },
    ];
    const fixture = render(highlights, 'uk', { limit: 2 });
    const element: HTMLElement = fixture.nativeElement;
    const toggle = () =>
      element.querySelector('[data-testid="highlights-toggle"]') as HTMLButtonElement;

    expect(element.querySelectorAll('[data-testid="highlight"]')).toHaveLength(2);
    expect(toggle().textContent!.trim()).toBe('Усі 4');
    expect(toggle().getAttribute('aria-expanded')).toBe('false');

    toggle().click();
    fixture.detectChanges();

    expect(element.querySelectorAll('[data-testid="highlight"]')).toHaveLength(4);
    expect(toggle().textContent!.trim()).toBe('Згорнути');
    expect(toggle().getAttribute('aria-expanded')).toBe('true');
  });

  it('ac4_5_offers_no_toggle_when_the_list_fits', () => {
    const element: HTMLElement = render(facultyAnalytics().highlights, 'en', {
      limit: 5,
    }).nativeElement;

    expect(element.querySelector('[data-testid="highlights-toggle"]')).toBeNull();
  });
});
