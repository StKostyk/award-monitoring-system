import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { paneStub } from '../../../shared/layout/pane.testing';
import { languageStub, sampleAnalytics, translations } from '../analytics.testing';
import { CategoryChartComponent, categoryRows } from './category-chart.component';

describe('CategoryChartComponent', () => {
  function setUp(language: 'en' | 'uk', byCategory = sampleAnalytics().byCategory) {
    const pane = paneStub();
    TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, translations(language)],
      providers: [pane.provider, languageStub(language).provider],
    });
    const fixture = TestBed.createComponent(CategoryChartComponent);
    fixture.componentRef.setInput('byCategory', byCategory);
    fixture.detectChanges();
    return { fixture, pane: pane.pane };
  }

  function many() {
    const template = sampleAnalytics().byCategory[0];
    return Array.from({ length: 15 }, (_, index) => ({
      category: { ...template.category, id: index + 1, name: `Category ${index + 1}` },
      count: 20 - index,
    }));
  }

  it('ranks_categories_by_count_then_name_with_whole_per_cent_shares', () => {
    const rows = categoryRows(
      [
        { category: { id: 1, name: 'B', nameUk: 'Б', level: 'FACULTY' }, count: 1 },
        { category: { id: 2, name: 'A', nameUk: 'А', level: 'FACULTY' }, count: 1 },
        { category: { id: 3, name: 'C', nameUk: 'В', level: 'NATIONAL' }, count: 4 },
      ],
      'en',
    );

    expect(rows.map((row) => [row.rank, row.name, row.share])).toEqual([
      [1, 'C', 67],
      [2, 'A', 17],
      [3, 'B', 17],
    ]);
    expect(categoryRows([], 'en')).toEqual([]);
  });

  it('ac2_5_shows_the_most_frequent_categories_as_cards_highest_first', () => {
    const { fixture } = setUp('uk');
    const element: HTMLElement = fixture.nativeElement;
    const cards = [...element.querySelectorAll('[data-testid="category-card"]')];

    expect(cards.map((card) => card.querySelector('.cats__name')!.textContent)).toEqual([
      'Грант',
      'Диплом',
    ]);
    expect(cards[0].classList).toContain('cats__card--first');
    expect(cards[0].textContent).toContain('67 % усіх нагород');
    expect(element.querySelector('[data-testid="category-rest"]')).toBeNull();
    expect(element.querySelector('[data-testid="category-table"]')).toBeNull();
  });

  it('ac4_5_puts_three_cards_and_the_rest_as_bars_and_every_category_in_the_table', () => {
    const { fixture, pane } = setUp('en', many());
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelectorAll('[data-testid="category-card"]')).toHaveLength(3);
    const bars = element.querySelectorAll('[data-testid="category-rest"] [data-testid="bar-row"]');
    expect(bars).toHaveLength(12);
    expect(bars[0].textContent).toContain('Category 4');

    pane.view.set('table');
    fixture.detectChanges();

    expect(element.querySelector('[data-testid="category-cards"]')).toBeNull();
    const rows = element.querySelectorAll('[data-testid="category-table"] tbody tr');
    expect(rows).toHaveLength(15);
    expect(rows[0].textContent).toContain('Category 1');
  });

  it('ac2_5_names_the_level_in_the_table_in_ukrainian', () => {
    const { fixture, pane } = setUp('uk');
    pane.view.set('table');
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="category-table"]').textContent,
    ).toContain('Національний');
  });
});
