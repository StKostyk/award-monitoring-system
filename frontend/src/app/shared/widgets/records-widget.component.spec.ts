import { vi } from 'vitest';

import { records } from './facts';
import { RecordsWidgetComponent } from './records-widget.component';
import { byTestId, renderWidget } from './widgets.testing';

describe('RecordsWidgetComponent', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('ac5_4_shows_each_record_as_a_card_with_label_value_and_holder', async () => {
    const found = records(
      {
        period: { from: 2022, to: 2026 },
        byYear: [
          { year: 2024, total: 40 },
          { year: 2025, total: 35 },
        ],
        byMonth: [],
        byLevel: [{ level: 'NATIONAL', count: 4 }],
        today: { year: 2026, month: 9 },
      },
      { name: 'Факультет математики та інформатики', count: 80 },
    );
    const { fixture } = await renderWidget(RecordsWidgetComponent, { records: found });

    const cards = Array.from(
      byTestId(fixture, 'records')!.querySelectorAll('[data-testid="record-card"]'),
      (card) =>
        ['label', 'value', 'holder']
          .map((part) => card.querySelector(`.record__${part}`)!.textContent!.trim())
          .join(' | '),
    );
    expect(cards).toEqual([
      'Рекордний рік | 2024 | нагород: 40',
      'Найбільше нагород | Факультет математики та інформатики | нагород: 80',
      'Найвищий рівень | Національний | за 2022–2026',
    ]);
    const values = fixture.nativeElement.querySelectorAll('.record__value');
    expect(values[0].classList).not.toContain('record__value--long');
    expect(values[1].classList).toContain('record__value--long');
  });

  it('ac2_4_shows_an_empty_state_without_records', async () => {
    const { text } = await renderWidget(RecordsWidgetComponent, { records: [] });

    expect(text()).toContain('Ще немає даних за цей період.');
  });
});
