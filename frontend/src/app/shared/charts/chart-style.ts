import { ChartPalette } from './chart-theme.service';

/** Bars and markers end in a 4 px radius; stacked segments and neighbours keep a 2 px surface gap. */
export const BAR_RADIUS = 4;
export const MARK_GAP = 2;

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

/** Text of a tooltip line, escaped so that award titles cannot inject markup. */
export function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
