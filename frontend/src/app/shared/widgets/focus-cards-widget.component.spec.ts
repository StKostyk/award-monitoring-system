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

  it('ac5_3_ac5_4_offers_a_command_button_disabled_when_the_card_says_so_and_no_action_when_none', async () => {
    const { fixture } = await renderWidget(FocusCardsWidgetComponent, {
      cards: [
        {
          id: 'unit-41',
          title: 'Кафедра алгебри',
          line: 'Жодної нагороди у 2026',
          action: { label: 'Нагадати завідувачу', command: '41' },
        },
        {
          id: 'unit-42',
          title: 'Кафедра геометрії',
          line: 'Жодної нагороди у 2026',
          action: { label: 'Нагадано 17.11.2026', command: '42', disabled: true },
        },
        { id: 'unit-43', title: 'Факультет', line: 'Жодної нагороди у 2026', action: null },
      ],
    });
    const commands: string[] = [];
    fixture.componentInstance.command.subscribe((command) => commands.push(command));

    const enabled = byTestId(fixture, 'focus-unit-41')!.querySelector('button')!;
    const disabled = byTestId(fixture, 'focus-unit-42')!.querySelector('button')!;
    enabled.click();
    disabled.click();

    expect(commands).toEqual(['41']);
    expect(disabled.disabled).toBe(true);
    expect(disabled.textContent?.trim()).toBe('Нагадано 17.11.2026');
    expect(byTestId(fixture, 'focus-unit-43')!.querySelector('a, button')).toBeNull();
  });

  it('ac2_5_shows_an_empty_state_without_cards', async () => {
    const { text } = await renderWidget(FocusCardsWidgetComponent, { cards: [] });

    expect(text()).toContain('Нічого не чекає на вашу увагу.');
  });
});
