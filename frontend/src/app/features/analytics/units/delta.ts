import { Figure } from './unit-analytics.service';

export type DeltaDirection = 'up' | 'down' | 'same';

/** How a figure changed against the previous period, ready to print. */
export interface DeltaView {
  direction: DeltaDirection;
  /** The signed count, e.g. `+3`, `−2` or `0`. */
  count: string;
  /** The signed percentage, e.g. `+60 %`; null when the previous count is 0. */
  percent: string | null;
  /** Whether the figure is new: nothing in the previous period, something in this one. */
  fresh: boolean;
}

/**
 * The printable change of a figure.
 *
 * @param figure the figure of the period
 * @returns its direction, signed count and percentage
 */
export function deltaView(figure: Figure): DeltaView {
  const direction: DeltaDirection = figure.delta > 0 ? 'up' : figure.delta < 0 ? 'down' : 'same';
  return {
    direction,
    count: signed(figure.delta),
    percent:
      figure.deltaPercent === null ? null : `${signed(roundHalfAway(figure.deltaPercent))} %`,
    fresh: figure.previous === 0 && figure.current > 0,
  };
}

function roundHalfAway(value: number): number {
  return Math.sign(value) * Math.round(Math.abs(value));
}

function signed(value: number): string {
  if (value > 0) {
    return `+${value}`;
  }
  return value < 0 ? `−${Math.abs(value)}` : '0';
}
