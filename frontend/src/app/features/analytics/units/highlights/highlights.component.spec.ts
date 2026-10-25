import { TestBed } from '@angular/core/testing';

import { languageStub, translations } from '../../analytics.testing';
import { UnitHighlight } from '../unit-analytics.service';
import { ALGEBRA, ANALYSIS, facultyAnalytics, ref } from '../unit-analytics.testing';
import { HighlightsComponent } from './highlights.component';

describe('HighlightsComponent', () => {
  function render(highlights: UnitHighlight[], language: 'en' | 'uk' = 'en') {
    TestBed.configureTestingModule({
      imports: [translations(language)],
      providers: [languageStub(language).provider],
    });
    const fixture = TestBed.createComponent(HighlightsComponent);
    fixture.componentRef.setInput('highlights', highlights);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('ac2_6_reads_each_highlight_as_a_sentence_in_strengths_and_gaps', () => {
    const element = render([
      ...facultyAnalytics().highlights,
      { type: 'NO_AWARDS', unit: ref(ALGEBRA), current: 0, previous: 2 },
      { type: 'BELOW_AVERAGE', unit: ref(ANALYSIS), current: 1, previous: 1 },
    ]);
    const strengths = element.querySelector('[data-testid="highlights-strengths"]')!;
    const gaps = element.querySelector('[data-testid="highlights-gaps"]')!;

    expect(strengths.textContent).toContain(
      'Department of Algebra and Informatics: 11 awards, 5 in the previous period',
    );
    expect(gaps.textContent).toContain(
      'Department of Mathematical Analysis: 3 awards, 8 in the previous period',
    );
    expect(gaps.textContent).toContain('no awards in this period, 2 in the previous one');
    expect(gaps.textContent).toContain("under half of the faculty's average per member");
    expect(gaps.querySelectorAll('li')).toHaveLength(3);
  });

  it('ac2_6_says_no_awards_in_either_period_without_a_previous_count_of_zero', () => {
    const element = render([{ type: 'NO_AWARDS', unit: ref(ALGEBRA), current: 0, previous: 0 }]);

    expect(element.textContent).toContain(
      'Department of Algebra and Informatics: no awards in this period or the previous one',
    );
  });

  it('ac2_6_names_the_department_in_ukrainian', () => {
    const element = render(facultyAnalytics().highlights, 'uk');

    expect(element.textContent).toContain(
      'Кафедра математичного аналізу: нагород — 3, у попередньому періоді — 8',
    );
  });

  it('ac2_6_says_when_nothing_stands_out', () => {
    const element = render([]);

    expect(element.querySelector('[data-testid="highlights-none"]')!.textContent).toContain(
      'Nothing stands out in this period.',
    );
  });

  it('ac2_6_university_counts_above_average_among_the_strengths_with_its_own_sentences', () => {
    TestBed.configureTestingModule({
      imports: [translations('en')],
      providers: [languageStub('en').provider],
    });
    const fixture = TestBed.createComponent(HighlightsComponent);
    fixture.componentRef.setInput('highlights', [
      { type: 'ABOVE_AVERAGE', unit: ref(ALGEBRA), current: 40, previous: 20 },
      { type: 'BELOW_AVERAGE', unit: ref(ANALYSIS), current: 1, previous: 1 },
    ]);
    fixture.componentRef.setInput('sentenceKeys', 'universityAnalytics.highlights');
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('[data-testid="highlights-strengths"]')!.textContent).toContain(
      "Department of Algebra and Informatics: 40 awards, at least one and a half times the university's average",
    );
    expect(element.querySelector('[data-testid="highlights-gaps"]')!.textContent).toContain(
      "under half of the university's average per member",
    );
  });
});
