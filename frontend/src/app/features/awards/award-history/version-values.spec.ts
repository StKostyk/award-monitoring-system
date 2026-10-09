import { AwardVersion } from '../awards.service';
import { ValueNames, organizationIds, shownValue } from './version-values';

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
    expect(shownValue('impactScore', 80, names)).toEqual({ text: '80' });
  });

  it('shows_the_award_date_day_first_in_the_interface_language', () => {
    expect(shownValue('awardDate', '2025-05-01', { ...names, language: 'uk' })).toEqual({
      text: '01.05.2025',
    });
    expect(shownValue('awardDate', '2025-05-01', names)).toEqual({ text: '01/05/2025' });
    expect(shownValue('awardDate', '2025-5-1', names)).toEqual({ text: '2025-5-1' });
  });

  it('ac2_8_translates_status_and_badge', () => {
    expect(shownValue('status', 'PENDING', names)).toEqual({ key: 'awards.status.PENDING' });
    expect(shownValue('verificationBadge', true, names)).toEqual({ key: 'awards.history.yes' });
    expect(shownValue('verificationBadge', false, names)).toEqual({ key: 'awards.history.no' });
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
