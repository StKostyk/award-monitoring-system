import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { Achievement, AchievementsService } from '../../../achievements/achievements.service';
import { languageStub, translations } from '../../analytics.testing';
import { UnitAnalytics } from '../unit-analytics.service';
import { facultyAnalytics } from '../unit-analytics.testing';
import { TopPerformersComponent } from './top-performers.component';

function award(title: string, awardDate: string): Achievement {
  return { awardId: 1, title, titleUk: null, awardDate } as Achievement;
}

describe('TopPerformersComponent', () => {
  let awards: Achievement[];
  let list: ReturnType<typeof vi.fn>;

  function render(topPerformers: UnitAnalytics['topPerformers'], language: 'en' | 'uk' = 'en') {
    awards = awards ?? [];
    list = vi.fn(() =>
      of({ content: awards, totalElements: awards.length, totalPages: 1, size: 3 }),
    );
    TestBed.configureTestingModule({
      imports: [translations(language)],
      providers: [
        languageStub(language).provider,
        provideRouter([]),
        { provide: AchievementsService, useValue: { list } },
      ],
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

  function card(): HTMLElement | null {
    return document.querySelector('[data-testid="top-card"]');
  }

  function focusFirst(element: HTMLElement): HTMLAnchorElement {
    const link = element.querySelector<HTMLAnchorElement>('[data-testid="top-performer-link"]')!;
    link.dispatchEvent(new FocusEvent('focus'));
    TestBed.tick();
    return link;
  }

  it('ac4_3_links_every_person_to_their_portfolio', () => {
    const element = render(facultyAnalytics().topPerformers);
    const link = element.querySelector('[data-testid="top-performer-link"]')!;
    const userId = facultyAnalytics().topPerformers.people[0].userId;

    expect(link.tagName).toBe('A');
    expect(link.getAttribute('href')).toBe(`/public/achievements?person=${userId}`);
  });

  it('ac4_3_shows_a_card_with_the_count_and_the_latest_awards_on_focus', () => {
    awards = [award('Grant', '2025-03-01'), award('Best paper', '2024-06-01')];
    const element = render(facultyAnalytics().topPerformers);
    const link = focusFirst(element);

    expect(list).toHaveBeenCalledTimes(1);
    expect(list.mock.calls[0][0].filters.person).toBe(
      facultyAnalytics().topPerformers.people[0].userId,
    );
    expect(list.mock.calls[0][0].size).toBe(3);
    expect(list.mock.calls[0][1]).toBe('public');
    expect(card()!.textContent).toContain('Koval Olena');
    expect(card()!.textContent).toContain('Awards in the period: 6');
    expect(card()!.textContent).toContain('Portfolio →');
    const items = [...card()!.querySelectorAll('[data-testid="top-card-awards"] li')];
    expect(items.map((item) => [...item.children].map((part) => part.textContent!.trim()))).toEqual(
      [
        ['Grant', '2025'],
        ['Best paper', '2024'],
      ],
    );
    expect(link.getAttribute('aria-describedby')).toBe('top-card');

    link.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    TestBed.tick();
    expect(card()).toBeNull();

    focusFirst(element);
    expect(list).toHaveBeenCalledTimes(1);
  });

  it('ac4_3_shows_the_count_only_when_every_award_is_private', () => {
    awards = [];
    const element = render(facultyAnalytics().topPerformers);
    const link = focusFirst(element);

    expect(card()!.textContent).toContain('Awards in the period: 6');
    expect(card()!.querySelector('[data-testid="top-card-awards"]')).toBeNull();

    link.dispatchEvent(new FocusEvent('blur'));
    TestBed.tick();
    expect(card()).toBeNull();
  });

  it('ac4_3_loads_the_latest_awards_again_after_a_failed_request', () => {
    awards = [award('Grant', '2025-03-01')];
    const element = render(facultyAnalytics().topPerformers);
    list.mockReturnValueOnce(throwError(() => new Error('offline')));
    const link = focusFirst(element);

    expect(card()!.textContent).toContain('Awards in the period: 6');
    expect(card()!.querySelector('[data-testid="top-card-awards"]')).toBeNull();

    link.dispatchEvent(new FocusEvent('blur'));
    focusFirst(element);
    expect(list).toHaveBeenCalledTimes(2);
    expect(card()!.querySelector('[data-testid="top-card-awards"]')!.textContent).toContain(
      'Grant',
    );
  });

  it('ac4_3_keeps_the_card_of_the_focused_person_after_the_pointer_passes_another_row', () => {
    const template = facultyAnalytics().topPerformers.people[0];
    const people = Array.from({ length: 4 }, (_, index) => ({
      ...template,
      userId: index + 1,
      firstName: `Name${index + 1}`,
    }));
    const element = render({ status: 'LISTED', people });
    const links = element.querySelectorAll<HTMLAnchorElement>('[data-testid="top-performer-link"]');
    links[0].dispatchEvent(new FocusEvent('focus'));
    links[3].dispatchEvent(new MouseEvent('mouseenter'));
    TestBed.tick();
    expect(card()!.textContent).toContain('Name4');

    links[3].dispatchEvent(new MouseEvent('mouseleave'));
    TestBed.tick();
    expect(card()!.textContent).toContain('Name1');
    expect(links[0].getAttribute('aria-describedby')).toBe('top-card');
  });
});
