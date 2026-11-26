import { Achievement } from './achievements.service';
import { groupByYear, pickHighlight } from './highlight';

function item(awardId: number, awardDate: string, level: string): Achievement {
  return {
    awardId,
    title: `Award ${awardId}`,
    titleUk: null,
    description: null,
    descriptionUk: null,
    category: { id: 1, name: 'Category', nameUk: null, level },
    awardingOrganization: 'ЧНУ',
    awardDate,
    externalUrl: null,
    verified: false,
    recipient: {
      type: 'PERSON',
      personName: 'Олена Коваль',
      unit: { id: 64, name: 'Algebra', nameUk: null, type: 'DEPARTMENT' },
    },
  } as Achievement;
}

describe('highlight', () => {
  it('ac3_2_picks_the_highest_level_of_the_month_and_the_newest_among_equals', () => {
    const items = [
      item(5, '2026-11-18', 'FACULTY'),
      item(4, '2026-11-12', 'NATIONAL'),
      item(3, '2026-11-03', 'NATIONAL'),
      item(2, '2026-10-30', 'INTERNATIONAL'),
    ];

    expect(pickHighlight(items, '2026-11')?.awardId).toBe(4);
  });

  it('ac3_2_prefers_the_higher_id_on_the_same_day', () => {
    expect(
      pickHighlight(
        [item(8, '2026-11-12', 'REGIONAL'), item(9, '2026-11-12', 'REGIONAL')],
        '2026-11',
      )?.awardId,
    ).toBe(9);
  });

  it('ac3_2_finds_nothing_without_awards_this_month', () => {
    expect(pickHighlight([item(2, '2026-10-30', 'INTERNATIONAL')], '2026-11')).toBeNull();
    expect(pickHighlight([], '2026-11')).toBeNull();
  });

  it('ac3_3_groups_by_award_year_newest_first_keeping_the_list_order', () => {
    const groups = groupByYear([
      item(5, '2026-02-01', 'FACULTY'),
      item(4, '2026-01-01', 'FACULTY'),
      item(3, '2024-06-01', 'FACULTY'),
    ]);

    expect(groups.map((group) => [group.year, group.items.map((each) => each.awardId)])).toEqual([
      [2026, [5, 4]],
      [2024, [3]],
    ]);
  });
});
