import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { vi } from 'vitest';

import { ChartThemeService } from '../../../shared/charts/chart-theme.service';
import { chartStub, chartsDrawn } from '../../../shared/charts/chart.testing';
import { languageStub, translations } from '../../analytics/analytics.testing';
import { RetentionRun } from '../compliance.service';
import { runDurations } from './run-durations';
import { RunDurationsComponent } from './run-durations.component';

interface DrawnOption {
  xAxis: { data: string[] };
  series: { data: { value: number; itemStyle: { color: string } }[] }[];
}

const BAR = 'rgb(10, 10, 10)';
const ERROR = 'rgb(200, 0, 0)';

function run(
  id: number,
  seconds: number | null,
  overrides: Partial<RetentionRun> = {},
): RetentionRun {
  const startedAt = `2026-11-${String(id).padStart(2, '0')}T01:00:00Z`;
  return {
    id,
    status: 'COMPLETED',
    startedAt,
    finishedAt:
      seconds === null ? null : new Date(Date.parse(startedAt) + seconds * 1000).toISOString(),
    results: [],
    ...overrides,
  };
}

describe('Retention run durations', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('ac7_2_turns_the_newest_runs_into_seconds_oldest_first_without_a_run_in_progress', () => {
    const runs = [
      run(3, 42),
      run(2, null, { status: 'RUNNING' }),
      run(1, 90, { status: 'PARTIAL' }),
    ];

    expect(runDurations(runs)).toEqual([
      { id: 1, startedAt: runs[2].startedAt, seconds: 90, failed: true },
      { id: 3, startedAt: runs[0].startedAt, seconds: 42, failed: false },
    ]);
  });

  it('ac7_2_counts_a_run_with_a_failed_policy_as_failed', () => {
    const failing = run(4, 5, {
      results: [{ policy: 'audit-logs', affected: 0, held: 0, failed: true, error: 'timeout' }],
    });

    expect(runDurations([failing])[0].failed).toBe(true);
  });

  it('ac7_2_draws_a_failed_run_in_the_error_colour_with_a_table', async () => {
    const stub = chartStub();
    TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, translations('uk')],
      providers: [
        stub.provider,
        languageStub('uk').provider,
        {
          provide: ChartThemeService,
          useValue: {
            reducedMotion: signal(true),
            palette: signal({
              text: BAR,
              mutedText: BAR,
              grid: BAR,
              surface: BAR,
              bar: BAR,
              error: ERROR,
              bands: [],
              series: [],
              fontFamily: '',
            }),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(RunDurationsComponent);
    fixture.componentRef.setInput('runs', [run(2, 30), run(1, 75, { status: 'INTERRUPTED' })]);
    fixture.detectChanges();
    await chartsDrawn();
    const option = stub.options.at(-1) as unknown as DrawnOption;

    expect(option.series[0].data.map((bar) => bar.value)).toEqual([75, 30]);
    expect(option.series[0].data.map((bar) => bar.itemStyle.color)).toEqual([ERROR, BAR]);
    expect(option.xAxis.data).toHaveLength(2);

    fixture.nativeElement.querySelector('[data-testid="chart-table-switch"] button').click();
    fixture.detectChanges();
    const rows = Array.from<HTMLElement>(
      fixture.nativeElement.querySelectorAll('[data-testid="run-duration-row"]'),
    );
    expect(rows.map((row) => row.dataset['failed'])).toEqual(['true', 'false']);
    expect(rows[0].textContent).toContain('75');
  });
});
