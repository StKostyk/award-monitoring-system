import { vi } from 'vitest';

import { reduceMotion } from '../effects/effects.testing';
import { KpiWidgetComponent, monthSpan, sparkPoints } from './kpi-widget.component';
import { byTestId, renderWidget } from './widgets.testing';

const FALLING = {
  year: 2026,
  months: 10,
  current: 79,
  previous: 111,
  delta: -32,
  deltaPercent: -28.83,
};

describe('KpiWidgetComponent', () => {
  beforeEach(() => reduceMotion());
  afterEach(() => vi.unstubAllGlobals());

  it('ac2_2_shows_the_number_the_sparkline_and_the_change_against_the_same_months', async () => {
    const { fixture } = await renderWidget(KpiWidgetComponent, {
      label: 'Затверджено цього року',
      value: 79,
      years: [20, 25, 40, 35, 79],
      delta: FALLING,
    });

    expect(byTestId(fixture, 'kpi-value')?.textContent).toBe('79');
    expect(byTestId(fixture, 'kpi-spark')?.getAttribute('aria-hidden')).toBe('true');
    const delta = byTestId(fixture, 'kpi-delta')!.textContent!.replace(/\s+/g, ' ');
    expect(delta).toContain('↓ 29 %');
    expect(delta).toContain('менше на 29 %');
    expect(delta).toContain('79 за січень–жовтень 2026');
    expect(delta).toContain('111 за ті самі місяці 2025');
  });

  it('ac2_2_shows_no_percentage_when_the_previous_count_is_0', async () => {
    const { fixture } = await renderWidget(KpiWidgetComponent, {
      label: 'Затверджено',
      value: 3,
      delta: { year: 2026, months: 1, current: 3, previous: 0, delta: 3, deltaPercent: null },
    });

    const delta = byTestId(fixture, 'kpi-delta')!.textContent!;
    expect(delta).not.toContain('%');
    expect(delta).toContain('3 за січень 2026');
    expect(delta).toContain('0 за ті самі місяці 2025');
    expect(byTestId(fixture, 'kpi-spark')).toBeNull();
  });

  it('ac2_6_shows_the_final_number_at_once_under_reduced_motion', async () => {
    const { fixture } = await renderWidget(KpiWidgetComponent, { label: 'Усього', value: 140 });

    expect(byTestId(fixture, 'kpi-value')?.textContent).toBe('140');
    expect(byTestId(fixture, 'kpi-delta')).toBeNull();
  });

  it('ac2_2_scales_the_sparkline_to_its_box', () => {
    expect(sparkPoints([0, 10, 5])).toBe('0,30 50,2 100,16');
    expect(sparkPoints([0, 0])).toBe('0,30 100,30');
  });

  it('ac1_names_the_compared_months_in_both_languages', () => {
    expect(monthSpan('uk', 10)).toBe('січень–жовтень');
    expect(monthSpan('en', 10)).toBe('January–October');
    expect(monthSpan('en', 1)).toBe('January');
    expect(monthSpan('uk', 12)).toBe('січень–грудень');
  });
});
