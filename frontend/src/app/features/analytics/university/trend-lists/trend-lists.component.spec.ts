import { TestBed } from '@angular/core/testing';

import { languageStub, translations } from '../../analytics.testing';
import { AnalyticsTrends } from '../university-analytics.service';
import { universityAnalytics } from '../university-analytics.testing';
import { TrendListsComponent } from './trend-lists.component';

describe('TrendListsComponent', () => {
  function render(trends: AnalyticsTrends, language: 'en' | 'uk' = 'en'): HTMLElement {
    TestBed.configureTestingModule({
      imports: [translations(language)],
      providers: [languageStub(language).provider],
    });
    const fixture = TestBed.createComponent(TrendListsComponent);
    fixture.componentRef.setInput('trends', trends);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function text(element: HTMLElement, testId: string): string {
    return (
      element.querySelector(`[data-testid="${testId}"]`)?.textContent?.replace(/\s+/g, ' ') ?? ''
    );
  }

  it('ac2_5_lists_rising_and_falling_entries_with_both_counts_and_the_signed_delta', () => {
    const element = render(universityAnalytics().trends);

    expect(text(element, 'trends-categories-rising')).toContain('Grant');
    expect(text(element, 'trends-categories-rising')).toContain('40 (previously 20)');
    expect(text(element, 'trends-categories-rising')).toContain('+20');
    expect(text(element, 'trends-sources-rising')).toContain('NAS of Ukraine');
    expect(text(element, 'trends-sources-rising')).toContain('new');
    expect(text(element, 'trends-sources-falling')).toContain('2 (previously 9)');
    expect(text(element, 'trends-sources-falling')).toMatch(/[−-]7/);
  });

  it('ac2_5_says_when_nothing_changed_noticeably', () => {
    const element = render({
      categories: { rising: [], falling: [] },
      sources: { rising: [], falling: [] },
    });

    expect(text(element, 'trends-categories-rising-empty')).toContain(
      'No category changed noticeably.',
    );
    expect(text(element, 'trends-sources-falling-empty')).toContain(
      'No awarding organisation changed noticeably.',
    );
  });

  it('ac2_9_names_categories_in_ukrainian', () => {
    const element = render(universityAnalytics().trends, 'uk');

    expect(text(element, 'trends-categories-rising')).toContain('Грант');
    expect(text(element, 'trends-categories')).toContain('Зростають');
  });
});
