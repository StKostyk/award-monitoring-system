import { RetentionRun } from '../compliance.service';

/** One finished retention run as a bar of the duration chart. */
export interface RunDuration {
  id: number;
  startedAt: string;
  seconds: number;
  failed: boolean;
}

const MILLIS = 1000;

/**
 * The duration of each finished run, oldest first, so the chart reads left to right in time; a run still in
 * progress has no duration and is left out.
 *
 * @param runs the newest runs, newest first
 * @returns the bars of the duration chart
 */
export function runDurations(runs: RetentionRun[]): RunDuration[] {
  return runs
    .filter((run) => run.finishedAt !== null)
    .map((run) => ({
      id: run.id,
      startedAt: run.startedAt,
      seconds: Math.max(
        0,
        Math.round((Date.parse(run.finishedAt as string) - Date.parse(run.startedAt)) / MILLIS),
      ),
      failed: run.status !== 'COMPLETED' || run.results.some((result) => result.failed),
    }))
    .reverse();
}
