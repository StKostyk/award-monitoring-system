import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { vi } from 'vitest';

import { ChartStub, chartStub, chartsDrawn } from '../../../../shared/charts/chart.testing';
import { languageStub, translations } from '../../analytics.testing';
import { UniversityAnalytics } from '../university-analytics.service';
import { universityAnalytics } from '../university-analytics.testing';
import { ProjectionTrendChartComponent } from './projection-trend-chart.component';

interface LineSeries {
  name: string;
  data: (number | null)[];
  lineStyle: { type?: string };
}

describe('ProjectionTrendChartComponent', () => {
  let stub: ChartStub;
  let fixture: ComponentFixture<ProjectionTrendChartComponent>;

  afterEach(() => vi.unstubAllGlobals());

  function render(analytics: UniversityAnalytics): HTMLElement {
    stub = chartStub();
    TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, translations('en')],
      providers: [stub.provider, languageStub('en').provider],
    });
    fixture = TestBed.createComponent(ProjectionTrendChartComponent);
    fixture.componentRef.setInput('analytics', analytics);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function showTable(element: HTMLElement): void {
    (element.querySelector('[data-testid="chart-table-switch"] button') as HTMLElement).click();
    fixture.detectChanges();
  }

  it('ac2_3_draws_the_projection_as_a_dashed_segment_to_the_projected_year', async () => {
    const element = render(universityAnalytics());
    await chartsDrawn();
    const option = stub.options.at(-1) as { series: LineSeries[]; xAxis: { data: string[] } };

    expect(option.xAxis.data).toEqual(['2022', '2023', '2024', '2025', '2026']);
    expect(option.series[0].data).toEqual([20, 25, 30, 35, 30]);
    expect(option.series[1].lineStyle.type).toBe('dashed');
    expect(option.series[1].data).toEqual([null, null, null, 35, 40]);
    expect(element.querySelector('[data-testid="university-projection"]')!.textContent).toContain(
      'Projected for 2026: about 40 awards, based on 2022–2025.',
    );
  });

  it('ac2_8_marks_the_projected_point_in_the_table', () => {
    const element = render(universityAnalytics());
    showTable(element);
    const marked = element.querySelectorAll('[data-testid="projection-row"]');

    expect(marked).toHaveLength(1);
    expect(marked[0].textContent).toContain('about 40 (projected)');
  });

  it('ac2_3_adds_a_projected_year_after_the_period', async () => {
    const analytics = universityAnalytics();
    analytics.byYear = analytics.byYear.slice(0, 4);
    analytics.projection = { ...analytics.projection, basis: { from: 2022, to: 2025 }, year: 2026 };
    analytics.partialYear = false;
    render(analytics);
    await chartsDrawn();
    const option = stub.options.at(-1) as { series: LineSeries[]; xAxis: { data: string[] } };

    expect(option.xAxis.data.at(-1)).toBe('2026');
    expect(option.series[0].data.at(-1)).toBeNull();
  });

  it('ac2_3_says_when_there_are_too_few_complete_years', async () => {
    const analytics = universityAnalytics();
    analytics.projection = {
      status: 'TOO_FEW_YEARS',
      basis: null,
      slope: null,
      year: null,
      estimate: null,
    };
    const element = render(analytics);
    await chartsDrawn();
    const option = stub.options.at(-1) as { series: LineSeries[] };

    expect(option.series).toHaveLength(1);
    expect(element.querySelector('[data-testid="university-projection"]')!.textContent).toContain(
      'Too few complete years for a projection.',
    );
    showTable(element);

    expect(element.querySelector('[data-testid="projection-table"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="projection-row"]')).toBeNull();
  });
});
