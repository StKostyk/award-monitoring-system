import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { PersonalRank } from '../analytics.service';
import { ALGEBRA, languageStub, translations } from '../analytics.testing';
import { RankCardComponent } from './rank-card.component';

describe('RankCardComponent', () => {
  function render(rank: PersonalRank, language: 'en' | 'uk' = 'en'): HTMLElement {
    TestBed.configureTestingModule({
      imports: [RankCardComponent, translations(language)],
      providers: [provideRouter([]), languageStub(language).provider],
    });
    const fixture = TestBed.createComponent(RankCardComponent);
    fixture.componentRef.setInput('rank', rank);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  const ranked: PersonalRank = {
    status: 'RANKED',
    position: 2,
    groupSize: 12,
    sharedWith: 1,
    department: ALGEBRA,
  };

  it('ac2_7_reads_the_position_with_the_colleagues_sharing_it', () => {
    const card = render(ranked);

    expect(card.querySelector('[data-testid="rank-position"]')?.textContent?.trim()).toBe(
      '2 of 12 in Department of Algebra and Informatics',
    );
    expect(card.querySelector('[data-testid="rank-shared"]')?.textContent).toContain(': 1');
  });

  it('ac2_7_names_the_department_in_ukrainian_and_hides_the_share_when_alone', () => {
    const card = render({ ...ranked, position: 1, sharedWith: 0 }, 'uk');

    expect(card.querySelector('[data-testid="rank-position"]')?.textContent).toContain(
      '1 з 12 на кафедрі «Кафедра алгебри та інформатики»',
    );
    expect(card.querySelector('[data-testid="rank-shared"]')).toBeNull();
  });

  it('ac2_7_explains_the_consent_and_links_to_the_privacy_centre', () => {
    const card = render({
      status: 'CONSENT_REQUIRED',
      position: null,
      groupSize: null,
      sharedWith: null,
      department: null,
    });

    expect(card.querySelector('[data-testid="rank-consent"]')?.textContent).toContain('consent');
    expect(card.querySelector('[data-testid="rank-privacy-link"]')?.getAttribute('href')).toBe(
      '/privacy',
    );
    expect(card.querySelector('[data-testid="rank-position"]')).toBeNull();
  });

  it('ac2_7_explains_a_group_too_small_and_a_unit_without_department', () => {
    const small = render({ ...ranked, status: 'GROUP_TOO_SMALL', position: null, groupSize: null });

    expect(small.querySelector('[data-testid="rank-small"]')?.textContent).toContain(
      'Department of Algebra and Informatics has too few members',
    );

    TestBed.resetTestingModule();
    const none = render({ ...ranked, status: 'NO_DEPARTMENT', department: null });

    expect(none.querySelector('[data-testid="rank-no-department"]')?.textContent).toContain(
      'not a department',
    );
  });
});
