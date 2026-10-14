import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';

import en from '../../../../public/i18n/en.json';
import uk from '../../../../public/i18n/uk.json';
import { AppLanguage, LanguageService } from '../i18n/language.service';
import { AppNotification, NOTIFICATION_TYPES, NotificationType } from './notification.api';
import { NotificationText, notificationTarget, relativeTime } from './notification-text';

const award = { title: 'Best teacher', titleUk: 'Кращий викладач', reviewer: 'Аліна Мартинюк' };
const authority = {
  role: 'DEAN',
  organization: 'Faculty of Mathematics',
  organizationUk: 'Факультет математики',
  validTo: '2026-10-31',
};

function notification(
  type: NotificationType,
  payload: Record<string, unknown>,
  referenceId: number | null = null,
): AppNotification {
  return {
    id: 1,
    type,
    payload,
    referenceType: referenceId === null ? null : 'AWARD',
    referenceId,
    read: false,
    sentAt: '2026-10-14T09:00:00Z',
  };
}

function keys(value: unknown, prefix = ''): string[] {
  if (typeof value !== 'object' || value === null) {
    return [prefix];
  }
  return Object.entries(value).flatMap(([key, child]) =>
    keys(child, prefix ? `${prefix}.${key}` : key),
  );
}

describe('NotificationText', () => {
  const language = signal<AppLanguage>('en');
  let texts: NotificationText;

  function render(type: NotificationType, payload: Record<string, unknown>): string {
    return texts.text(notification(type, payload));
  }

  beforeEach(() => {
    language.set('en');
    TestBed.configureTestingModule({
      imports: [
        TranslocoTestingModule.forRoot({
          langs: { en, uk },
          translocoConfig: { availableLangs: ['uk', 'en'], defaultLang: 'en' },
          preloadLangs: true,
        }),
      ],
      providers: [{ provide: LanguageService, useValue: { current: language } }],
    });
    texts = TestBed.inject(NotificationText);
  });

  it('ac2_7_has_uk_and_en_keys_for_every_type_and_the_same_key_set', () => {
    const english = keys(en.notifications);

    expect(english).toEqual(keys(uk.notifications));
    for (const type of NOTIFICATION_TYPES) {
      expect(english).toContain(`types.${type}`);
    }
  });

  it('ac2_3_renders_every_award_type_from_the_payload', () => {
    expect(render('AWARD_APPROVED', { ...award, level: 'DEAN' })).toBe(
      '«Best teacher» approved by Аліна Мартинюк at the dean level',
    );
    expect(render('AWARD_REJECTED', { ...award, level: 'FACULTY_SECRETARY' })).toBe(
      '«Best teacher» rejected by Аліна Мартинюк at the faculty secretary level',
    );
    expect(render('AWARD_RETURNED', { ...award, level: 'DEAN' })).toContain('returned for changes');
    expect(render('AWARD_ESCALATED', { ...award, level: 'DEAN' })).toContain(
      'passed on from the dean level',
    );
    expect(render('AWARD_CORRECTED', { ...award, fields: ['title', 'awardDate'] })).toBe(
      '«Best teacher» corrected by Аліна Мартинюк: Title in English, Award date',
    );
    expect(render('REVIEW_TAKEN_OVER', award)).toBe(
      'The review of «Best teacher» was taken over by Аліна Мартинюк',
    );
  });

  it('ac2_3_renders_overdue_role_and_delegation_types', () => {
    expect(render('REVIEW_OVERDUE', { count: 3, oldestDeadline: '2026-10-01' })).toBe(
      'Requests past their review period: 3, oldest deadline 01/10/2026',
    );
    expect(render('REVIEW_OVERDUE', { count: 1, oldestDeadline: null })).toBe(
      'Requests past their review period: 1',
    );
    expect(render('ROLE_ASSIGNED', { ...authority, validTo: null })).toBe(
      'You have been given the role “Dean”: Faculty of Mathematics',
    );
    expect(render('ROLE_REVOKED', authority)).toBe(
      'Your role “Dean” has been revoked: Faculty of Mathematics, last day 31/10/2026',
    );
    expect(render('DELEGATION_CREATED', { ...authority, otherParty: 'Мартин Мартинюк' })).toBe(
      'Мартин Мартинюк has delegated the role “Dean” to you: Faculty of Mathematics, until 31/10/2026',
    );
    expect(render('DELEGATION_REVOKED', { ...authority, otherParty: 'Аліна' })).toContain(
      'with Аліна has been revoked',
    );
  });

  it('ac2_7_renders_in_ukrainian_after_the_language_switch', () => {
    TestBed.inject(TranslocoService).setActiveLang('uk');
    language.set('uk');

    expect(render('AWARD_APPROVED', { ...award, level: 'DEAN' })).toBe(
      `«Кращий викладач» схвалено (рівень: ${uk.awards.levels.DEAN}): Аліна Мартинюк`,
    );
    expect(render('ROLE_ASSIGNED', authority)).toBe(
      `Вам надано роль «${uk.roles.DEAN}»: Факультет математики, до 31.10.2026`,
    );
  });

  it('falls_back_to_the_other_title_and_raw_unknown_values', () => {
    expect(render('AWARD_APPROVED', { titleUk: 'Кращий', reviewer: 'A', level: 'DEPUTY' })).toBe(
      '«Кращий» approved by A at the DEPUTY level',
    );
    expect(
      texts.text({ ...notification('AWARD_APPROVED', {}), type: 'SYSTEM' as NotificationType }),
    ).toBe('New notification');
  });

  it('ac2_4_leads_each_type_to_its_page', () => {
    expect(notificationTarget(notification('AWARD_RETURNED', award, 5))).toEqual(['/awards', 5]);
    expect(notificationTarget(notification('REVIEW_TAKEN_OVER', award, 6))).toEqual(['/awards', 6]);
    expect(notificationTarget(notification('REVIEW_OVERDUE', {}))).toEqual(['/reviews']);
    expect(notificationTarget(notification('ROLE_REVOKED', authority))).toEqual(['/profile']);
    expect(notificationTarget(notification('DELEGATION_CREATED', authority))).toEqual([
      '/delegations',
    ]);
    expect(notificationTarget(notification('AWARD_APPROVED', award))).toBeNull();
  });

  it('ac2_3_tells_the_time_relative_to_now', () => {
    const now = Date.parse('2026-10-14T12:00:00Z');

    expect(relativeTime('2026-10-14T11:59:30Z', 'en', now)).toBe('now');
    expect(relativeTime('2026-10-14T11:55:00Z', 'en', now)).toBe('5 minutes ago');
    expect(relativeTime('2026-10-14T09:00:00Z', 'en', now)).toBe('3 hours ago');
    expect(relativeTime('2026-10-13T09:00:00Z', 'en', now)).toBe('yesterday');
    expect(relativeTime('2026-10-14T11:00:00Z', 'uk', now)).toBe('1 годину тому');
    expect(relativeTime('2026-09-01T09:00:00Z', 'en', now)).toBe('01/09/2026');
    expect(relativeTime('not a date', 'en', now)).toBe('');
  });
});
