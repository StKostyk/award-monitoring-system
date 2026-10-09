import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';

import { LanguageService } from '../../../core/i18n/language.service';
import { Achievement } from '../achievements.service';
import { AchievementCardComponent } from './achievement-card.component';

const personal: Achievement = {
  awardId: 7,
  title: null,
  titleUk: 'Грамота МОН',
  description: null,
  descriptionUk: 'За внесок у розвиток освіти',
  category: {
    id: 13,
    name: 'Ministry Recognition',
    nameUk: 'Відзнака міністерства',
    level: 'NATIONAL',
  },
  awardingOrganization: 'МОН України',
  awardDate: '2025-05-01',
  externalUrl: 'https://mon.gov.ua',
  verified: true,
  recipient: {
    type: 'PERSON',
    personName: 'Анастасія Коваль',
    unit: { id: 64, name: 'Algebra', nameUk: 'Кафедра алгебри', type: 'DEPARTMENT' },
  },
};

const unitAward: Achievement = {
  ...personal,
  awardId: 8,
  title: 'Best department',
  externalUrl: null,
  verified: false,
  recipient: { ...personal.recipient, type: 'UNIT', personName: null },
};

const translations = {
  uk: {
    achievements: { unitAward: 'Нагорода підрозділу', verified: 'Документи перевірено' },
    categories: { levels: { NATIONAL: 'Національний' } },
  },
  en: {},
};

describe('AchievementCardComponent', () => {
  let language: string;

  async function render(achievement: Achievement): Promise<HTMLElement> {
    await TestBed.configureTestingModule({
      imports: [
        AchievementCardComponent,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk', 'en'], defaultLang: 'uk' },
        }),
      ],
      providers: [{ provide: LanguageService, useValue: { current: () => language } }],
    }).compileComponents();
    const fixture: ComponentFixture<AchievementCardComponent> =
      TestBed.createComponent(AchievementCardComponent);
    fixture.componentRef.setInput('achievement', achievement);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  const text = (element: HTMLElement, id: string): string =>
    element.querySelector(`[data-testid="${id}"]`)?.textContent?.trim() ?? '';

  beforeEach(() => (language = 'uk'));

  it('ac1_8_shows_the_person_the_department_category_date_and_verification', async () => {
    const element = await render(personal);

    expect(text(element, 'achievement-title')).toBe('Грамота МОН');
    expect(text(element, 'achievement-recipient')).toContain('Анастасія Коваль');
    expect(text(element, 'achievement-unit')).toBe('Кафедра алгебри');
    expect(text(element, 'achievement-category')).toContain('Відзнака міністерства');
    expect(text(element, 'achievement-category')).toContain('Національний');
    expect(text(element, 'achievement-date')).not.toBe('');
    expect(text(element, 'achievement-verified')).toContain('Документи перевірено');
  });

  it('ac1_8_opens_the_link_in_a_new_tab_without_passing_the_page_on', async () => {
    const link = (await render(personal)).querySelector<HTMLAnchorElement>(
      '[data-testid="achievement-link"]',
    );

    expect(link?.href).toBe('https://mon.gov.ua/');
    expect(link?.target).toBe('_blank');
    expect(link?.rel).toBe('noopener noreferrer nofollow');
  });

  it('ac1_8_falls_back_to_the_ukrainian_title_in_english', async () => {
    language = 'en';
    const element = await render(personal);

    expect(text(element, 'achievement-title')).toBe('Грамота МОН');
    expect(text(element, 'achievement-unit')).toBe('Algebra');
  });

  it('ac1_8_names_the_unit_as_recipient_of_a_unit_award', async () => {
    const element = await render(unitAward);

    expect(text(element, 'achievement-unit')).toBe('Кафедра алгебри');
    expect(text(element, 'achievement-recipient')).toContain('Нагорода підрозділу');
    expect(element.querySelector('[data-testid="achievement-verified"]')).toBeNull();
    expect(element.querySelector('[data-testid="achievement-link"]')).toBeNull();
  });
});
