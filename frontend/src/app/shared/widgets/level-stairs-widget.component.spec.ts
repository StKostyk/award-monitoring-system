import { vi } from 'vitest';

import { levelSteps } from './facts';
import { LevelStairsWidgetComponent } from './level-stairs-widget.component';
import { byTestId, renderWidget } from './widgets.testing';

describe('LevelStairsWidgetComponent', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('ac2_5_shows_the_levels_as_rising_steps_from_the_unit_to_the_world', async () => {
    const steps = levelSteps([
      { level: 'DEPARTMENT', count: 3 },
      { level: 'FACULTY', count: 2 },
      { level: 'NATIONAL', count: 1 },
    ]);
    const { fixture } = await renderWidget(LevelStairsWidgetComponent, { steps });

    const list = byTestId(fixture, 'level-stairs')!;
    expect(list.getAttribute('aria-label')).toContain('від рівнів підрозділів до міжнародного');
    const items = Array.from(list.querySelectorAll('li'));
    expect(
      items.map((item) =>
        Array.from(item.children, (part) => part.textContent!.trim())
          .join(' ')
          .trim(),
      ),
    ).toEqual([
      '5 Рівні підрозділів',
      '0 Університетський',
      '0 Регіональний',
      '1 Національний',
      '0 Міжнародний',
    ]);
    const heights = items.map((item) => {
      const block = item.querySelector('.stairs__block') as HTMLElement;
      return parseFloat(block.style.getPropertyValue('--w'));
    });
    expect(heights).toEqual([...heights].sort((a, b) => a - b));
    expect(heights.at(-1)).toBe(100);
  });

  it('ac2_5_shows_an_empty_state_without_awards', async () => {
    const { fixture, text } = await renderWidget(LevelStairsWidgetComponent, {
      steps: levelSteps([]),
    });

    expect(byTestId(fixture, 'level-stairs')).toBeNull();
    expect(text()).toContain('Ще немає даних за цей період.');
  });
});
