import { TestBed } from '@angular/core/testing';

import { languageStub, translations } from '../../analytics.testing';
import { UnitAnalytics } from '../unit-analytics.service';
import { facultyAnalytics } from '../unit-analytics.testing';
import { TopPerformersComponent } from './top-performers.component';

describe('TopPerformersComponent', () => {
  function render(topPerformers: UnitAnalytics['topPerformers'], language: 'en' | 'uk' = 'en') {
    TestBed.configureTestingModule({
      imports: [translations(language)],
      providers: [languageStub(language).provider],
    });
    const fixture = TestBed.createComponent(TopPerformersComponent);
    fixture.componentRef.setInput('topPerformers', topPerformers);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('ac3_5_puts_the_first_three_on_a_podium_and_ranks_the_rest', () => {
    const template = facultyAnalytics().topPerformers.people[0];
    const people = Array.from({ length: 5 }, (_, index) => ({
      ...template,
      userId: index + 1,
      firstName: `Name${index + 1}`,
      count: 10 - index,
    }));
    const element = render({ status: 'LISTED', people }, 'uk');
    const cards = [...element.querySelectorAll('[data-testid="top-podium-card"]')];
    const rows = [...element.querySelectorAll('[data-testid="top-list-row"]')];

    expect(cards).toHaveLength(3);
    expect(cards.map((card) => card.querySelector('.podium__rank')!.textContent)).toEqual([
      '1',
      '2',
      '3',
    ]);
    expect(cards[0].classList).toContain('podium__card--1');
    expect(cards[0].querySelector('.avatar')!.textContent).toBe('KN');
    expect(rows).toHaveLength(2);
    expect(element.querySelector('[data-testid="top-list"]')!.getAttribute('start')).toBe('4');
    expect(rows[1].textContent).toContain('Koval Name5');
    expect(rows[1].querySelector('.ranks__count')!.textContent).toContain('6');
  });

  it('ac2_5_shows_one_person_without_a_position', () => {
    const element = render(facultyAnalytics().topPerformers, 'uk');
    const list = element.querySelector('[data-testid="top-performers"]')!;

    expect(list.querySelectorAll('[data-testid="top-podium-card"]')).toHaveLength(1);
    expect(list.querySelector('.podium__rank')).toBeNull();
    expect(list.querySelector('[data-testid="top-list"]')).toBeNull();
    expect(list.textContent).toContain('Koval Olena');
    expect(list.textContent).toContain('Кафедра алгебри та інформатики');
    expect(list.textContent).toContain('Нагород: 6');
    expect(list.textContent).toContain('Національний');
    expect(element.textContent).toContain('погодився бути названим');
  });

  it('ac2_5_says_when_nobody_agreed_to_be_named', () => {
    const element = render({ status: 'LISTED', people: [] });

    expect(element.querySelector('[data-testid="top-performers-empty"]')!.textContent).toContain(
      'Nobody in this unit has agreed to be named yet.',
    );
  });

  it('ac2_5_explains_a_group_too_small_without_figures', () => {
    const element = render({ status: 'GROUP_TOO_SMALL', people: [] });
    const text = element.querySelector('[data-testid="top-performers-too-small"]')!.textContent!;

    expect(text).toContain('minimum group');
    expect(text).not.toMatch(/\d/);
    expect(element.querySelector('[data-testid="top-performers"]')).toBeNull();
  });
});
