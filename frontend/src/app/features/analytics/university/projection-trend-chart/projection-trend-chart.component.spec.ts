import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { paneStub } from '../../../../shared/layout/pane.testing';
import { languageStub, translations } from '../../analytics.testing';
import { UniversityAnalytics } from '../university-analytics.service';
import { universityAnalytics } from '../university-analytics.testing';
import { ProjectionTrendChartComponent } from './projection-trend-chart.component';

describe('ProjectionTrendChartComponent', () => {
  let fixture: ComponentFixture<ProjectionTrendChartComponent>;
  let pane: ReturnType<typeof paneStub>;

  function render(analytics: UniversityAnalytics, language: 'en' | 'uk' = 'en'): HTMLElement {
    pane = paneStub();
    TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, translations(language)],
      providers: [pane.provider, languageStub(language).provider],
    });
    fixture = TestBed.createComponent(ProjectionTrendChartComponent);
    fixture.componentRef.setInput('analytics', analytics);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function showTable(): void {
    pane.pane.view.set('table');
    fixture.detectChanges();
  }

  function columns(element: HTMLElement): HTMLElement[] {
    return [
      ...element.querySelectorAll(
        '[data-testid="projection-column"], [data-testid="projection-column-running"]',
      ),
    ] as HTMLElement[];
  }

  function height(column: HTMLElement, part: string): string | null {
    return (column.querySelector(`.years__${part}`) as HTMLElement | null)?.style.height ?? null;
  }

  it('ac4_3_draws_a_column_per_year_with_its_number_and_hatches_the_projected_rest', () => {
    const element = render(universityAnalytics(), 'uk');
    const drawn = columns(element);

    expect(drawn.map((column) => column.querySelector('.years__year')!.textContent)).toEqual([
      '2022',
      '2023',
      '2024',
      '2025',
      '2026',
    ]);
    expect(drawn.map((column) => column.querySelector('.years__count')!.textContent)).toEqual([
      '20',
      '25',
      '30',
      '35',
      '30',
    ]);
    expect(element.querySelectorAll('[data-testid="projection-column-running"]')).toHaveLength(1);
    const running = drawn[4];
    expect(running.dataset['testid']).toBe('projection-column-running');
    expect(height(running, 'bar')).toBe('75%');
    expect(height(running, 'rest')).toBe('25%');
    expect(running.querySelector('.years__running')!.textContent!.trim()).toBe('рік триває');
    expect(height(drawn[0], 'rest')).toBeNull();
    expect(element.querySelector('.years__key')).not.toBeNull();
    expect(element.querySelector('[data-testid="projection-table"]')).toBeNull();
    expect(element.querySelector('[data-testid="university-projection"]')!.textContent).toContain(
      'Прогноз на 2026: близько 40 нагород, за даними 2022–2025.',
    );
  });

  it('ac2_8_marks_the_projected_point_in_the_table', () => {
    const element = render(universityAnalytics());
    showTable();
    const marked = element.querySelectorAll('[data-testid="projection-row"]');

    expect(element.querySelector('[data-testid="projection-columns"]')).toBeNull();
    expect(marked).toHaveLength(1);
    expect(marked[0].textContent).toContain('about 40 (projected)');
  });

  it('ac2_3_adds_a_projected_year_after_the_period', () => {
    const analytics = universityAnalytics();
    analytics.byYear = analytics.byYear.slice(0, 4);
    analytics.projection = { ...analytics.projection, basis: { from: 2022, to: 2025 }, year: 2026 };
    analytics.partialYear = false;
    const element = render(analytics);
    const last = columns(element).at(-1)!;

    expect(last.querySelector('.years__year')!.textContent).toBe('2026');
    expect(last.querySelector('.years__count')!.textContent).toBe('—');
    expect(height(last, 'bar')).toBe('0%');
    expect(height(last, 'rest')).toBe('100%');
  });

  it('ac4_3_shows_no_key_when_nothing_is_hatched', () => {
    const analytics = universityAnalytics();
    analytics.projection = { ...analytics.projection, estimate: 30 };
    const element = render(analytics);

    expect(height(columns(element)[4], 'rest')).toBeNull();
    expect(element.querySelector('.years__key')).toBeNull();
    expect(columns(element)[4].querySelector('.years__running')).not.toBeNull();
  });

  it('ac2_3_says_when_there_are_too_few_complete_years', () => {
    const analytics = universityAnalytics();
    analytics.projection = {
      status: 'TOO_FEW_YEARS',
      basis: null,
      slope: null,
      year: null,
      estimate: null,
    };
    const element = render(analytics);

    expect(element.querySelector('[data-testid="projection-column-running"]')).toBeNull();
    expect(element.querySelector('[data-testid="university-projection"]')!.textContent).toContain(
      'Too few complete years for a projection.',
    );
    showTable();

    expect(element.querySelector('[data-testid="projection-table"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="projection-row"]')).toBeNull();
  });
});
