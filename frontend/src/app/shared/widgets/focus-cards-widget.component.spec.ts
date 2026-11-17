import { vi } from 'vitest';

import { FocusCardsWidgetComponent } from './focus-cards-widget.component';
import { byTestId, renderWidget } from './widgets.testing';

describe('FocusCardsWidgetComponent', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('ac2_5_shows_each_card_with_a_title_a_line_and_one_action', async () => {
    const { fixture } = await renderWidget(FocusCardsWidgetComponent, {
      cards: [
        {
          title: 'Кафедра алгебри',
          line: 'Жодної нагороди цього року',
          action: { label: 'Відкрити аналітику', link: ['/analytics/units', 41] },
        },
        {
          title: 'Черга розгляду',
          line: '3 заявки чекають понад 5 днів',
          action: { label: 'До черги', link: '/approvals' },
        },
      ],
    });

    const cards = byTestId(fixture, 'focus-cards')!.querySelectorAll('li');
    expect(cards).toHaveLength(2);
    expect(cards[0].querySelector('h3')?.textContent).toBe('Кафедра алгебри');
    expect(cards[0].querySelector('p')?.textContent).toBe('Жодної нагороди цього року');
    const links = cards[0].querySelectorAll('a');
    expect(links).toHaveLength(1);
    expect(links[0].getAttribute('href')).toBe('/analytics/units/41');
    expect(cards[1].querySelector('a')?.getAttribute('href')).toBe('/approvals');
  });

  it('ac2_5_shows_an_empty_state_without_cards', async () => {
    const { text } = await renderWidget(FocusCardsWidgetComponent, { cards: [] });

    expect(text()).toContain('Нічого не чекає на вашу увагу.');
  });
});
