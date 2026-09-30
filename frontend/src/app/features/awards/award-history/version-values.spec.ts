import { AwardVersion } from '../awards.service';
import { ValueNames, kyivDateTime, organizationIds, shownValue } from './version-values';

const names: ValueNames = {
  categories: new Map([
    [
      13,
      { id: 13, name: 'Ministry Recognition', nameUk: 'Відзнака міністерства', level: 'NATIONAL' },
    ],
  ]),
  organizations: new Map([[64, { id: 64, name: 'Algebra', nameUk: 'Кафедра алгебри' }]]),
  language: 'en',
};

describe('version values', () => {
  it('ac2_1_shows_ids_by_name_and_empty_values_as_a_dash', () => {
    expect(shownValue('categoryId', 13, names)).toEqual({ text: 'Ministry Recognition' });
    expect(shownValue('categoryId', 99, names)).toEqual({ text: '#99' });
    expect(shownValue('organizationId', 64, { ...names, language: 'uk' })).toEqual({
      text: 'Кафедра алгебри',
    });
    expect(shownValue('organizationId', 70, names)).toEqual({ text: '#70' });
    expect(shownValue('title', null, names)).toEqual({ text: '—' });
    expect(shownValue('title', '', names)).toEqual({ text: '—' });
    expect(shownValue('awardDate', '2025-05-01', names)).toEqual({ text: '2025-05-01' });
    expect(shownValue('impactScore', 80, names)).toEqual({ text: '80' });
  });

  it('ac2_8_translates_status_and_badge', () => {
    expect(shownValue('status', 'PENDING', names)).toEqual({ key: 'awards.status.PENDING' });
    expect(shownValue('verificationBadge', true, names)).toEqual({ key: 'awards.history.yes' });
    expect(shownValue('verificationBadge', false, names)).toEqual({ key: 'awards.history.no' });
  });

  it('ac2_1_formats_times_in_kyiv_in_both_languages', () => {
    expect(kyivDateTime('2026-09-30T21:30:00Z', 'uk')).toBe('01.10.2026, 00:30');
    expect(kyivDateTime('2026-12-01T10:00:00Z', 'en')).toBe('01/12/2026, 12:00');
  });

  it('ac2_1_collects_the_organisations_of_snapshots_and_changes', () => {
    const version = {
      snapshot: { organizationId: 64 },
      changes: [
        { field: 'organizationId', from: 70, to: 64 },
        { field: 'title', from: 1, to: 2 },
      ],
    } as unknown as AwardVersion;

    expect(organizationIds([version])).toEqual([64, 70]);
  });
});
