import { vi } from 'vitest';

import { reduceMotion } from '../effects/effects.testing';
import { FactWidgetComponent } from './fact-widget.component';
import { byTestId, renderWidget } from './widgets.testing';

const GROWTH = { key: 'facts.growth', params: { percent: 15, year: 2025 } };
const SENTENCE = 'З початку року нагород на 15 % більше, ніж за ті самі місяці 2025 року.';

describe('FactWidgetComponent', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('ac2_6_shows_the_whole_fact_at_once_under_reduced_motion', async () => {
    reduceMotion();
    const { fixture } = await renderWidget(FactWidgetComponent, { fact: GROWTH });

    const fact = byTestId(fixture, 'fact')!;
    expect(fact.querySelector('[data-testid="typed-text-full"]')?.textContent).toBe(SENTENCE);
    expect(fact.querySelector('[aria-hidden="true"]')?.textContent).toBe(SENTENCE);
  });

  it('ac2_4_translates_the_level_of_a_fact_before_inserting_it', async () => {
    reduceMotion();
    const { fixture } = await renderWidget(FactWidgetComponent, {
      fact: {
        key: 'facts.levelShare',
        params: { percent: 20, period: '2022–2026' },
        keyParams: { level: 'categories.levels.INTERNATIONAL' },
      },
    });

    expect(byTestId(fixture, 'typed-text-full')?.textContent).toBe(
      '20 % нагород за 2022–2026 — рівня «Міжнародний».',
    );
  });

  it('ac2_6_gives_screen_readers_the_whole_fact_while_it_types', async () => {
    const { fixture } = await renderWidget(FactWidgetComponent, { fact: GROWTH });

    expect(byTestId(fixture, 'typed-text-full')?.textContent).toBe(SENTENCE);
  });

  it('ac2_4_shows_an_empty_state_when_no_generator_has_data', async () => {
    const { fixture, text } = await renderWidget(FactWidgetComponent, { fact: null });

    expect(byTestId(fixture, 'fact')).toBeNull();
    expect(text()).toContain('Ще немає даних за цей період.');
  });
});
