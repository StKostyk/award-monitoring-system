import { ColorScheme } from '../brand/color-scheme.service';

export type StoredColorScheme = 'SYSTEM' | 'LIGHT' | 'DARK';

export type DashboardId = 'MY_AWARDS' | 'HALL_OF_FAME' | 'COMMAND_CENTRE';

/** The widget order and the hidden widgets of one dashboard. */
export interface DashboardLayout {
  order: string[];
  hidden: string[];
}

export type DashboardLayouts = Partial<Record<DashboardId, DashboardLayout>>;

export interface DashboardPreferences {
  colorScheme: StoredColorScheme;
  layouts: DashboardLayouts;
}

/** The widgets of each dashboard in their default order. */
export const DASHBOARD_WIDGETS: Readonly<Record<DashboardId, readonly string[]>> = {
  MY_AWARDS: [
    'KPI',
    'FACT',
    'TREND',
    'LEVEL_STAIRS',
    'IN_PROGRESS',
    'LATEST',
    'BY_CATEGORY',
    'RANK',
    'VISIBILITY',
  ],
  HALL_OF_FAME: ['KPI', 'TREND', 'RACE', 'RECORDS', 'FACT', 'TOP', 'LEVEL_MAP'],
  COMMAND_CENTRE: ['TODAY', 'QUEUE', 'UNITS', 'TOP'],
};

/** The widgets of the personal dashboard's side column; the others fill its main column. */
export const MY_AWARDS_SIDE: readonly string[] = ['KPI', 'FACT'];

/**
 * The widgets in display order: the stored order first, then the widgets it misses in their default order.
 *
 * @param widgets the widgets in their default order
 * @param layout the stored layout, if any
 * @return the ordered widgets, unknown names dropped
 */
export function arrange(widgets: readonly string[], layout: DashboardLayout | undefined): string[] {
  const stored = (layout?.order ?? []).filter((widget) => widgets.includes(widget));
  const ordered = [...new Set(stored)];
  return [...ordered, ...widgets.filter((widget) => !ordered.includes(widget))];
}

/**
 * The scheme as the browser applies it.
 *
 * @param stored the scheme the account keeps
 * @return the browser scheme
 */
export function toColorScheme(stored: StoredColorScheme): ColorScheme {
  return stored.toLowerCase() as ColorScheme;
}

/**
 * The scheme as the account keeps it.
 *
 * @param scheme the browser scheme
 * @return the stored scheme
 */
export function toStoredColorScheme(scheme: ColorScheme): StoredColorScheme {
  return scheme.toUpperCase() as StoredColorScheme;
}
