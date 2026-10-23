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

  it('ac2_5_lists_name_department_count_and_highest_level_without_positions', () => {
    const element = render(facultyAnalytics().topPerformers, 'uk');
    const list = element.querySelector('[data-testid="top-performers"]')!;

    expect(list.tagName).toBe('UL');
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
