import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { TimelineEntry } from '../analytics.service';
import { languageStub, sampleAnalytics, translations } from '../analytics.testing';
import { LatestAwardsComponent } from './latest-awards.component';

describe('LatestAwardsComponent', () => {
  function render(
    timeline: TimelineEntry[],
    total: number,
    language: 'en' | 'uk' = 'en',
  ): HTMLElement {
    TestBed.configureTestingModule({
      imports: [LatestAwardsComponent, translations(language)],
      providers: [provideRouter([]), languageStub(language).provider],
    });
    const fixture = TestBed.createComponent(LatestAwardsComponent);
    fixture.componentRef.setInput('timeline', timeline);
    fixture.componentRef.setInput('total', total);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  function entries(count: number): TimelineEntry[] {
    const base = sampleAnalytics().timeline[0];
    return Array.from({ length: count }, (_, index) => ({
      ...base,
      awardId: 100 + index,
      title: `Award ${index}`,
      awardDate: `20${10 + index}-01-01`,
    })).reverse();
  }

  it('ac2_2_lists_the_five_newest_awards_newest_first_with_a_link_to_each', () => {
    const card = render(entries(7), 7);

    const rows = Array.from(
      card.querySelectorAll<HTMLAnchorElement>('[data-testid="latest-award"]'),
    );
    expect(rows.map((row) => row.getAttribute('href'))).toEqual([
      '/awards/106',
      '/awards/105',
      '/awards/104',
      '/awards/103',
      '/awards/102',
    ]);
    expect(rows[0].textContent).toContain('Award 6');
  });

  it('ac2_2_shows_issuer_date_and_level_chip_in_the_active_language', () => {
    const card = render(sampleAnalytics().timeline, 3, 'uk');

    const first = card.querySelector('[data-testid="latest-award"]') as HTMLElement;
    expect(first.textContent).toContain('Національний грант');
    expect(first.textContent).toContain('Ministry of Education');
    expect(first.textContent).toContain('01.03.2026');
    expect(first.querySelector('[data-testid="latest-level"]')?.textContent?.trim()).toBe(
      'Національний',
    );
  });

  it('ac2_2_falls_back_to_the_other_title_when_one_is_missing', () => {
    const card = render(sampleAnalytics().timeline, 3, 'en');

    const titles = Array.from(card.querySelectorAll('[data-testid="latest-award"]')).map(
      (row) => row.textContent,
    );
    expect(titles[1]).toContain('Подяка кафедри');
  });

  it('ac2_2_links_all_awards_with_the_approved_count', () => {
    const card = render(entries(2), 12);

    const all = card.querySelector<HTMLAnchorElement>('[data-testid="latest-all"]');
    expect(all?.getAttribute('href')).toBe('/awards?status=APPROVED');
    expect(all?.textContent).toContain('All 12');
  });

  it('ac2_2_shows_the_empty_state_without_approved_awards', () => {
    const card = render([], 0);

    expect(card.querySelector('[data-testid="latest-award"]')).toBeNull();
    expect(card.querySelector('[data-testid="latest-empty"]')).not.toBeNull();
    expect(card.querySelector('[data-testid="latest-all"]')).toBeNull();
  });
});
