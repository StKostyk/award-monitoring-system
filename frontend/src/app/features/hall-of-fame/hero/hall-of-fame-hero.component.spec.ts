import { vi } from 'vitest';

import { reduceMotion } from '../../../shared/effects/effects.testing';
import { byTestId, renderWidget } from '../../../shared/widgets/widgets.testing';
import { HallOfFameHeroComponent, monthsBegun } from './hall-of-fame-hero.component';

const NOVEMBER_2026 = { year: 2026, month: 11 };
const AHEAD = {
  year: 2026,
  months: 10,
  current: 356,
  previous: 290,
  delta: 66,
  deltaPercent: 22.76,
};

describe('HallOfFameHeroComponent', () => {
  beforeEach(() => reduceMotion());
  afterEach(() => vi.unstubAllGlobals());

  it('ac5_1_shows_the_count_the_months_ring_and_the_change_against_the_same_months', async () => {
    const { fixture } = await renderWidget(HallOfFameHeroComponent, {
      year: 2026,
      value: 356,
      today: NOVEMBER_2026,
      delta: AHEAD,
    });

    expect(byTestId(fixture, 'kpi-value')?.textContent).toBe('356');
    const ring = byTestId(fixture, 'hero-ring')!;
    expect(ring.getAttribute('aria-label')).toBe('Минуло місяців: 11 з 12');
    expect(ring.textContent).toContain('11/12');
    const delta = byTestId(fixture, 'kpi-delta')!.textContent!.replace(/\s+/g, ' ');
    expect(delta).toContain('+66 · +23 %');
    expect(delta).toContain('290');
    const versus = (fixture.nativeElement as HTMLElement)
      .querySelector('.hero__vs')!
      .textContent!.replace(/\s+/g, ' ');
    expect(versus).toMatch(/356 .*2026/);
    expect(versus).toMatch(/290 .*2025/);
  });

  it('ac5_1_fills_the_ring_by_the_share_of_the_months_begun', async () => {
    const { fixture } = await renderWidget(HallOfFameHeroComponent, {
      year: 2026,
      value: 30,
      today: { year: 2026, month: 3 },
    });

    const fill = byTestId(fixture, 'hero-ring')!.querySelector('.hero__ring-fill')!;
    const [filled, length] = fill.getAttribute('stroke-dasharray')!.split(' ').map(Number);
    expect(filled / length).toBeCloseTo(3 / 12, 2);
    expect(byTestId(fixture, 'kpi-delta')).toBeNull();
  });

  it('ac5_1_counts_every_month_of_a_past_year_and_none_of_a_future_one', () => {
    expect(monthsBegun(2025, NOVEMBER_2026)).toBe(12);
    expect(monthsBegun(2026, NOVEMBER_2026)).toBe(11);
    expect(monthsBegun(2027, NOVEMBER_2026)).toBe(0);
  });
});
