import { ChartPalette } from './chart-theme.service';

/** Bars and markers end in a 4 px radius; stacked segments and neighbours keep a 2 px surface gap. */
export const BAR_RADIUS = 4;
export const MARK_GAP = 2;

/** Bar charts draw at most this many bars of their own before the rest forms one "Other" bar. */
export const MAX_BARS = 12;

const BAR_ROW_HEIGHT = 32;
const BAR_CHART_PADDING = 48;

/** A value axis: recessive grid lines, muted labels, whole numbers only. */
export function valueAxis(palette: ChartPalette): Record<string, unknown> {
  return {
    type: 'value',
    minInterval: 1,
    axisLabel: { color: palette.mutedText },
    splitLine: { lineStyle: { color: palette.grid } },
  };
}

/** A category axis with muted labels and no tick marks. */
export function categoryAxis(
  palette: ChartPalette,
  data: string[],
  labelWidth?: number,
): Record<string, unknown> {
  return {
    type: 'category',
    data,
    axisTick: { show: false },
    axisLine: { lineStyle: { color: palette.grid } },
    axisLabel: {
      color: palette.mutedText,
      ...(labelWidth ? { width: labelWidth, overflow: 'truncate' } : {}),
    },
  };
}

/** A tooltip on the surface colour with theme text, confined to the chart. */
export function tooltip(palette: ChartPalette, trigger: 'item' | 'axis'): Record<string, unknown> {
  return {
    trigger,
    confine: true,
    backgroundColor: palette.surface,
    borderColor: palette.grid,
    textStyle: { color: palette.text },
    ...(trigger === 'axis' ? { axisPointer: { type: 'shadow' } } : {}),
  };
}

/** The height in pixels of a horizontal bar chart with the given number of bars. */
export function barChartHeight(bars: number): number {
  return bars * BAR_ROW_HEIGHT + BAR_CHART_PADDING;
}

/** A horizontal bar chart of one series, the first bar on top, each bar labelled with its value. */
export function horizontalBarOption(
  palette: ChartPalette,
  bars: { label: string; value: number }[],
  seriesName: string,
  labelWidth?: number,
): Record<string, unknown> {
  const bottomUp = [...bars].reverse();
  return {
    grid: { left: 8, right: 24, top: 8, bottom: 8, containLabel: true },
    tooltip: tooltip(palette, 'item'),
    xAxis: valueAxis(palette),
    yAxis: categoryAxis(
      palette,
      bottomUp.map((bar) => bar.label),
      labelWidth,
    ),
    series: [
      {
        type: 'bar',
        name: seriesName,
        barMaxWidth: 20,
        itemStyle: { color: palette.bar, borderRadius: [0, BAR_RADIUS, BAR_RADIUS, 0] },
        label: { show: true, position: 'right', color: palette.text },
        data: bottomUp.map((bar) => bar.value),
      },
    ],
  };
}

/**
 * The first `MAX_BARS` rows, followed by one row that combines the rest when there are more.
 *
 * @param rows the rows, largest first
 * @param combine builds the "Other" row from the rows beyond the limit
 * @returns the rows to draw
 */
export function withOther<T>(rows: T[], combine: (rest: T[]) => T): T[] {
  return rows.length > MAX_BARS
    ? [...rows.slice(0, MAX_BARS), combine(rows.slice(MAX_BARS))]
    : rows;
}

/** Text of a tooltip line, escaped so that award titles cannot inject markup. */
export function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
