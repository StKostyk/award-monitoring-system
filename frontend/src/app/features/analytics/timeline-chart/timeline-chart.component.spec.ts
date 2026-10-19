import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { ChartStub, chartStub, chartsDrawn } from '../../../shared/charts/chart.testing';
import { languageStub, sampleAnalytics, translations } from '../analytics.testing';
import { TimelineChartComponent } from './timeline-chart.component';

describe('TimelineChartComponent', () => {
  let fixture: ComponentFixture<TimelineChartComponent>;
  let stub: ChartStub;

  beforeEach(async () => {
    stub = chartStub();
    TestBed.configureTestingModule({
      imports: [TimelineChartComponent, NoopAnimationsModule, translations('en')],
      providers: [provideRouter([]), stub.provider, languageStub('en').provider],
    });
    fixture = TestBed.createComponent(TimelineChartComponent);
    fixture.componentRef.setInput('timeline', sampleAnalytics().timeline);
    fixture.detectChanges();
    await chartsDrawn();
  });

  afterEach(() => vi.unstubAllGlobals());

  function showTable(): HTMLElement {
    fixture.nativeElement.querySelector('[data-testid="chart-table-switch"] button').click();
    fixture.detectChanges();
    return fixture.nativeElement.querySelector('[data-testid="timeline-table"]');
  }

  it('ac2_6_links_every_entry_of_the_table_to_its_award', () => {
    const table = showTable();
    const links = [...table.querySelectorAll('[data-testid="timeline-link"]')];

    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/awards/31',
      '/awards/22',
      '/awards/10',
    ]);
    expect(links.map((link) => link.textContent?.trim())).toEqual([
      'National grant',
      'Подяка кафедри',
      'Faculty diploma',
    ]);
    expect(table.querySelectorAll('.analytics-verified')).toHaveLength(2);
    expect(table.textContent).toContain('Grant (National)');
  });

  it('ac2_6_plots_by_date_and_level_and_describes_the_chart', () => {
    const option = stub.options.at(-1) as {
      xAxis: { min: (extent: { min: number }) => number; max: (extent: { max: number }) => number };
      yAxis: { data: string[] };
      series: { data: [string, number][] }[];
      aria: { label: { description: string } };
    };

    expect(option.yAxis.data).toEqual(['Department', 'Faculty', 'National']);
    expect(option.series[0].data).toEqual([
      ['2026-03-01', 2],
      ['2026-01-15', 0],
      ['2024-05-10', 1],
    ]);
    expect(option.aria.label.description).toContain('3 approved awards');
    expect(option.xAxis.min({ min: Date.UTC(2024, 4, 10) })).toBeLessThan(Date.UTC(2024, 3, 1));
    expect(option.xAxis.max({ max: Date.UTC(2026, 2, 1) })).toBeGreaterThan(Date.UTC(2026, 3, 1));
  });

  it('ac2_6_escapes_the_tooltip_and_opens_a_clicked_point', () => {
    const option = stub.options.at(-1) as {
      tooltip: { formatter: (params: { dataIndex: number }) => string };
    };

    expect(option.tooltip.formatter({ dataIndex: 0 })).toContain('<strong>National grant</strong>');
    expect(option.tooltip.formatter({ dataIndex: 9 })).toBe('');

    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.debugElement
      .query((node) => node.name === 'app-chart')
      .triggerEventHandler('pointClicked', { dataIndex: 1 });

    expect(navigate).toHaveBeenCalledWith(['/awards', 22]);
  });

  it('ac2_6_says_when_only_the_newest_awards_are_shown', () => {
    expect(fixture.nativeElement.querySelector('[data-testid="timeline-truncated"]')).toBeNull();

    fixture.componentRef.setInput('truncated', true);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="timeline-truncated"]').textContent,
    ).toContain('newest 3 awards');
  });
});
