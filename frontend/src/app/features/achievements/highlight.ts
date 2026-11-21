import { RECOGNITION_LEVELS, RecognitionLevel } from '../awards/awards.service';
import { Achievement } from './achievements.service';

/** Achievements of one award year, in the order the list gave them. */
export interface YearGroup {
  year: number;
  items: Achievement[];
}

/**
 * The achievement of the month: the highest level among those dated in the month, the newest among equals.
 *
 * @param items the achievements to choose from
 * @param month the month as `YYYY-MM`
 * @returns the achievement, or null when none is dated in the month
 */
export function pickHighlight(items: Achievement[], month: string): Achievement | null {
  const rank = (item: Achievement): number =>
    RECOGNITION_LEVELS.indexOf(item.category.level as RecognitionLevel);
  return items
    .filter((item) => item.awardDate.startsWith(`${month}-`))
    .reduce<Achievement | null>((best, item) => {
      if (best === null) {
        return item;
      }
      const order =
        rank(item) - rank(best) ||
        item.awardDate.localeCompare(best.awardDate) ||
        item.awardId - best.awardId;
      return order > 0 ? item : best;
    }, null);
}

/**
 * The achievements grouped by award year, newest year first.
 *
 * @param items the achievements, newest first
 * @returns one group per year
 */
export function groupByYear(items: Achievement[]): YearGroup[] {
  const groups = new Map<number, Achievement[]>();
  for (const item of items) {
    const year = Number(item.awardDate.slice(0, 4));
    const group = groups.get(year);
    if (group) {
      group.push(item);
    } else {
      groups.set(year, [item]);
    }
  }
  return [...groups.entries()]
    .sort(([a], [b]) => b - a)
    .map(([year, grouped]) => ({ year, items: grouped }));
}
