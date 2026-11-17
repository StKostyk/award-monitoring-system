import { vi } from 'vitest';

import { records } from './facts';
import { RecordsWidgetComponent } from './records-widget.component';
import { byTestId, renderWidget } from './widgets.testing';

describe('RecordsWidgetComponent', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('ac2_4_lists_the_records_as_translated_sentences', async () => {
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

    const items = Array.from(byTestId(fixture, 'records')!.querySelectorAll('li'), (item) =>
      item.textContent!.trim(),
    );
    expect(items).toEqual([
      'Рекордний рік — 2024 (нагород: 40)',
      'Найбільше нагород — Факультет математики та інформатики (80)',
      'Найвищий рівень за 2022–2026 — «Національний»',
    ]);
  });

  it('ac2_4_shows_an_empty_state_without_records', async () => {
    const { text } = await renderWidget(RecordsWidgetComponent, { records: [] });

    expect(text()).toContain('Ще немає даних за цей період.');
  });
});
