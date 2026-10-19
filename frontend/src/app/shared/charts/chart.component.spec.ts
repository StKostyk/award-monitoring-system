import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { vi } from 'vitest';

import { translations } from '../../features/analytics/analytics.testing';
import { ChartComponent } from './chart.component';
import { ChartStub, chartStub, chartsDrawn } from './chart.testing';
import { ChartThemeService } from './chart-theme.service';

@Component({
  imports: [ChartComponent],
  template: `
    <app-chart
      [option]="option()"
      description="Bars of awards by year"
      (pointClicked)="clicked = $event"
    >
      <table data-testid="projected">
        <tr>
          <td>2026</td>
        </tr>
      </table>
    </app-chart>
  `,
})
class HostComponent {
  readonly option = signal<Record<string, unknown>>({ series: [{ type: 'bar', data: [1] }] });
  clicked: unknown = null;
}

describe('ChartComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  let stub: ChartStub;
  const reducedMotion = signal(false);

  beforeEach(async () => {
    stub = chartStub();
    reducedMotion.set(false);
    await TestBed.configureTestingModule({
      imports: [HostComponent, NoopAnimationsModule, translations('en')],
      providers: [
        stub.provider,
        {
          provide: ChartThemeService,
          useValue: {
            reducedMotion,
            palette: signal({
              text: 'rgb(0, 0, 0)',
              mutedText: 'rgb(1, 1, 1)',
              grid: 'rgb(2, 2, 2)',
              surface: 'rgb(3, 3, 3)',
              bar: 'rgb(4, 4, 4)',
              bands: [],
              fontFamily: 'Nunito, sans-serif',
            }),
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    await chartsDrawn();
  });

  afterEach(() => vi.unstubAllGlobals());

  function element(testId: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  it('ac2_6_renders_svg_with_an_aria_description_and_theme_text', () => {
    const canvas = element('chart-canvas')!;

    expect(canvas.getAttribute('role')).toBe('img');
    expect(canvas.getAttribute('aria-label')).toBe('Bars of awards by year');
    expect(stub.initOpts[0]).toEqual({ renderer: 'svg' });
    expect(stub.options.at(-1)).toMatchObject({
      animation: true,
      textStyle: { color: 'rgb(0, 0, 0)', fontFamily: 'Nunito, sans-serif' },
      aria: { enabled: true, label: { description: 'Bars of awards by year' } },
      series: [{ type: 'bar', data: [1] }],
    });
  });

  it('ac2_6_turns_animation_off_under_reduced_motion', async () => {
    reducedMotion.set(true);
    fixture.detectChanges();
    await chartsDrawn();

    expect(stub.options.at(-1)).toMatchObject({ animation: false });
  });

  it('ac2_6_switches_to_the_projected_table_and_back', () => {
    expect(element('projected')).toBeNull();

    const toggle = element('chart-table-switch')!.querySelector('button')!;
    toggle.click();
    fixture.detectChanges();

    expect(element('projected')?.textContent).toContain('2026');
    expect(element('chart-canvas')).toBeNull();
    expect(element('chart-table-switch')?.textContent).toContain('Show as table');

    toggle.click();
    fixture.detectChanges();

    expect(element('projected')).toBeNull();
  });
});
