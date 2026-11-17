import { vi } from 'vitest';

import { TopWidgetComponent, initials } from './top-widget.component';
import { byTestId, renderWidget } from './widgets.testing';

const PEOPLE = [
  { name: 'Олена Коваль', count: 8 },
  { name: 'Іван Петренко', count: 6 },
  { name: 'Марія Шевчук', count: 4 },
  { name: 'Андрій Бойко', count: 4 },
  { name: 'Ірина Мельник', count: 2 },
  { name: 'Тарас Лисенко', count: 1 },
];

describe('TopWidgetComponent', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('ac2_5_shows_up_to_five_people_as_bars_with_initials_and_no_photos', async () => {
    const { fixture } = await renderWidget(TopWidgetComponent, { people: PEOPLE });

    const list = byTestId(fixture, 'top')!;
    const rows = list.querySelectorAll('li');
    expect(rows).toHaveLength(5);
    expect(rows[0].querySelector('.top__initials')?.textContent).toBe('ОК');
    expect(rows[0].textContent).toContain('Олена Коваль');
    expect(rows[0].textContent).toContain('Нагороди: 8');
    expect((rows[0].querySelector('.top__fill') as HTMLElement).style.width).toBe('100%');
    expect((rows[4].querySelector('.top__fill') as HTMLElement).style.width).toBe('25%');
    expect(list.querySelector('img')).toBeNull();
  });

  it('ac2_5_takes_the_first_letters_of_the_first_two_words', () => {
    expect(initials('  ольга   іванівна петрук ')).toBe('ОІ');
    expect(initials('ФМІ')).toBe('Ф');
  });

  it('ac2_5_shows_an_empty_state_without_people', async () => {
    const { fixture, text } = await renderWidget(TopWidgetComponent, {
      people: [{ name: 'Олена Коваль', count: 0 }],
    });

    expect(byTestId(fixture, 'top')).toBeNull();
    expect(text()).toContain('Ще немає даних за цей період.');
  });
});
