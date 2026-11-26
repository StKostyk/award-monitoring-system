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
    const { fixture } = await renderWidget(FactWidgetComponent, { facts: [GROWTH] });

    const fact = byTestId(fixture, 'fact')!;
    expect(fact.querySelector('[data-testid="typed-text-full"]')?.textContent).toBe(SENTENCE);
    expect(fact.querySelector('[aria-hidden="true"]')?.textContent).toBe(SENTENCE);
  });

  it('ac2_4_translates_the_level_of_a_fact_before_inserting_it', async () => {
    reduceMotion();
    const { fixture } = await renderWidget(FactWidgetComponent, {
      facts: [
        {
          key: 'facts.levelShare',
          params: { percent: 20, period: '2022–2026' },
          keyParams: { level: 'categories.levels.INTERNATIONAL' },
        },
      ],
    });

    expect(byTestId(fixture, 'typed-text-full')?.textContent).toBe(
      '20 % нагород за 2022–2026 — рівня «Міжнародний».',
    );
  });

  it('ac2_6_gives_screen_readers_the_whole_fact_while_it_types', async () => {
    const { fixture } = await renderWidget(FactWidgetComponent, { facts: [GROWTH] });

    expect(byTestId(fixture, 'typed-text-full')?.textContent).toBe(SENTENCE);
  });

  it('ac3_lists_every_fact_it_is_given', async () => {
    reduceMotion();
    const { fixture } = await renderWidget(FactWidgetComponent, {
      facts: [
        { key: 'facts.bestYear', params: { year: 2024, count: 40 } },
        { key: 'facts.firstAward', params: { year: 2019, period: '2019–2026' } },
        { key: 'facts.streak', params: { years: 8 } },
        GROWTH,
      ],
    });

    const items = [...fixture.nativeElement.querySelectorAll('[data-testid="typed-text-full"]')];
    expect(items.map((item) => item.textContent)).toEqual([
      'Найкращий рік — 2024 (нагород: 40).',
      'Перша нагорода за 2019–2026 — у 2019 році.',
      'Років поспіль із нагородами: 8.',
      SENTENCE,
    ]);
  });

  it('ac2_4_shows_an_empty_state_when_no_generator_has_data', async () => {
    const { fixture, text } = await renderWidget(FactWidgetComponent, { facts: [] });

    expect(byTestId(fixture, 'fact')).toBeNull();
    expect(text()).toContain('Ще немає даних за цей період.');
  });
});
