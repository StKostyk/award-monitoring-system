/** Approved awards of one calendar month. */
export interface MonthCount {
  year: number;
  /** The month, 1 to 12. */
  month: number;
  count: number;
}

/** Approved awards of one calendar year. */
export interface YearTotal {
  year: number;
  total: number;
}

/** Approved awards of one recognition level. */
export interface LevelCount {
  level: string;
  count: number;
}

/** A sentence to show: a translation key with its values; `keyParams` hold keys translated before they are inserted. */
export interface Fact {
  key: string;
  params: Record<string, string | number>;
  keyParams?: Record<string, string>;
}

/** A person of a top list, by name only. */
export interface TopPerson {
  name: string;
  count: number;
}

/** A card that points at one piece of work: a title, a line about it and one action. */
export interface FocusCard {
  title: string;
  line: string;
  action: { label: string; link: string | unknown[] };
}

/** One bar, step or area of a widget: a translation key for its label and its count. */
export interface LabelledCount {
  key: string;
  count: number;
}

/** A calendar month, 1 to 12. */
export interface CalendarMonth {
  year: number;
  month: number;
}
