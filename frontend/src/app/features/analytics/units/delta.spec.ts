import { deltaView } from './delta';
import { figure } from './unit-analytics.testing';

describe('deltaView', () => {
  it('ac2_3_signs_the_count_and_the_rounded_percentage', () => {
    expect(deltaView(figure(8, 5))).toEqual({
      direction: 'up',
      count: '+3',
      percent: '+60 %',
      fresh: false,
    });
    expect(deltaView(figure(3, 8))).toEqual({
      direction: 'down',
      count: '−5',
      percent: '−63 %',
      fresh: false,
    });
    expect(deltaView(figure(4, 4))).toEqual({
      direction: 'same',
      count: '0',
      percent: '0 %',
      fresh: false,
    });
  });

  it('ac2_3_calls_a_figure_new_when_the_previous_period_had_none', () => {
    expect(deltaView(figure(2, 0))).toEqual({
      direction: 'up',
      count: '+2',
      percent: null,
      fresh: true,
    });
    expect(deltaView(figure(0, 0)).fresh).toBe(false);
  });
});
