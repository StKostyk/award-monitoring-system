import { vi } from 'vitest';

import { levelAreas } from './facts';
import { LevelMapWidgetComponent } from './level-map-widget.component';
import { byTestId, lastOption, renderWidget, rowTexts, showData } from './widgets.testing';

const AREAS = levelAreas([
  { level: 'FACULTY', count: 80 },
  { level: 'NATIONAL', count: 40 },
  { level: 'REGIONAL', count: 0 },
]);

interface TreemapOption {
  series: { type: string; data: { name: string; value: number }[] }[];
}

describe('LevelMapWidgetComponent', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('ac2_5_draws_the_levels_as_a_treemap_with_area_by_count', async () => {
    const { stub, fixture } = await renderWidget(LevelMapWidgetComponent, { areas: AREAS });

    const series = lastOption<TreemapOption>(stub).series[0];
    expect(series.type).toBe('treemap');
    expect(series.data).toEqual([
      expect.objectContaining({ name: 'Факультетський', value: 80 }),
      expect.objectContaining({ name: 'Національний', value: 40 }),
    ]);
    expect(byTestId(fixture, 'chart-canvas')?.getAttribute('aria-label')).toContain(
      '«Факультетський» (80)',
    );
  });

  it('ac2_6_shows_the_levels_as_a_table_behind_data', async () => {
    const { fixture } = await renderWidget(LevelMapWidgetComponent, { areas: AREAS });

    expect(rowTexts(showData(fixture)!)).toEqual(['Факультетський 80', 'Національний 40']);
  });

  it('ac2_5_shows_an_empty_state_without_awards', async () => {
    const { fixture, text } = await renderWidget(LevelMapWidgetComponent, { areas: [] });

    expect(byTestId(fixture, 'chart-canvas')).toBeNull();
    expect(text()).toContain('Ще немає даних за цей період.');
  });
});
