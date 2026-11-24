import { monthCounts } from '../../features/analytics/units/unit-analytics.testing';
import { monthNames } from '../date-format';
import { RaceWidgetComponent } from './race-widget.component';
import { byTestId, renderWidget, rowTexts, showView } from './widgets.testing';

const BY_MONTH = [
  ...monthCounts(2025, [2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3]),
  ...monthCounts(2026, [1, 1, 6, 4, 4, 4, 4, 3, 3]),
];

describe('RaceWidgetComponent', () => {
  async function race(byMonth = BY_MONTH, month = 9) {
    return renderWidget(RaceWidgetComponent, {
      byMonth,
      year: 2026,
      today: { year: 2026, month },
    });
  }

  function turn(fixture: Parameters<typeof byTestId>[0]): string {
    return Array.from(byTestId(fixture, 'race-turn')!.children, (part) => part.textContent!.trim())
      .join(' ')
      .replace(/\s+/g, ' ');
  }

  it('ac5_3_shows_the_score_of_both_years_up_to_this_month_and_the_overtaking_month', async () => {
    const { fixture } = await race();

    expect(byTestId(fixture, 'race-current')!.textContent).toBe('30');
    expect(byTestId(fixture, 'race-previous')!.textContent).toBe('26');
    const bars = Array.from(fixture.nativeElement.querySelectorAll('[data-testid="bar-row"]'));
    expect(
      bars.map((row) => (row as HTMLElement).querySelector('.bars__count')!.textContent),
    ).toEqual(['30', '26']);
    expect((bars[0] as HTMLElement).classList).toContain('bars__row--current');
    expect(fixture.nativeElement.querySelector('.bars--thick')).not.toBeNull();
    expect(turn(fixture)).toBe(`2026 обігнав 2025 у місяці «${monthNames('uk', 'long')[3]}» +4`);
    expect(byTestId(fixture, 'race-widget-table')).toBeNull();
  });

  it('ac5_3_says_ahead_behind_or_level_without_a_crossing', async () => {
    const ahead = await race([...monthCounts(2025, [1, 1]), ...monthCounts(2026, [3, 3])], 2);
    expect(turn(ahead.fixture)).toBe('2026 попереду 2025 на 4 +4');
  });

  it('ac5_3_says_behind_with_a_signed_gap', async () => {
    const behind = await race([...monthCounts(2025, [3, 3]), ...monthCounts(2026, [1, 1])], 2);
    expect(turn(behind.fixture)).toBe('2026 відстає від 2025 на 4 −4');
  });

  it('ac5_3_says_level_when_the_counts_match', async () => {
    const level = await race([...monthCounts(2025, [2, 2]), ...monthCounts(2026, [2, 2])], 2);
    expect(turn(level.fixture)).toBe('2026 і 2025 нарівні 0');
  });

  it('ac2_6_shows_the_months_as_a_table_in_the_table_view', async () => {
    const { fixture } = await race();

    showView(fixture);

    const rows = rowTexts(byTestId(fixture, 'race-widget-table')!);
    expect(rows).toHaveLength(12);
    expect(rows[8]).toBe(`${monthNames('uk')[8]} 30 26`);
    expect(rows[11]).toBe(`${monthNames('uk')[11]}  35`);
    expect(byTestId(fixture, 'race-score')).toBeNull();
  });

  it('ac2_5_shows_an_empty_state_without_awards', async () => {
    const { text } = await race([], 3);

    expect(text()).toContain('Ще немає даних за цей період.');
  });
});
