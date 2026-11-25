import { BreakpointObserver } from '@angular/cdk/layout';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { TimelineEntry } from '../analytics.service';
import { languageStub, sampleAnalytics, translations } from '../analytics.testing';
import { TimelineChartComponent } from './timeline-chart.component';

describe('TimelineChartComponent', () => {
  let fixture: ComponentFixture<TimelineChartComponent>;

  function render(
    wide: boolean,
    timeline: TimelineEntry[] = sampleAnalytics().timeline,
  ): HTMLElement {
    TestBed.configureTestingModule({
      imports: [TimelineChartComponent, translations('en')],
      providers: [
        provideRouter([]),
        languageStub('en').provider,
        {
          provide: BreakpointObserver,
          useValue: { observe: () => of({ matches: wide, breakpoints: {} }) },
        },
      ],
    });
    fixture = TestBed.createComponent(TimelineChartComponent);
    fixture.componentRef.setInput('timeline', timeline);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  function entries(count: number): TimelineEntry[] {
    const base = sampleAnalytics().timeline[0];
    return Array.from({ length: count }, (_, index) => ({
      ...base,
      awardId: 100 + index,
      title: `Award ${index}`,
      awardDate: `20${10 + index}-04-0${(index % 9) + 1}`,
    })).reverse();
  }

  function texts(element: HTMLElement, selector: string): string[] {
    return [...element.querySelectorAll(selector)].map((node) =>
      (node.textContent ?? '').replace(/\s+/g, ' ').trim(),
    );
  }

  it('ac3_2_draws_a_road_from_the_first_award_to_today_at_960_px_and_wider', () => {
    const element = render(true);
    const stops = [...element.querySelectorAll<HTMLAnchorElement>('[data-testid="timeline-stop"]')];

    expect(element.querySelector('[data-testid="timeline-feed"]')).toBeNull();
    expect(stops.map((stop) => stop.getAttribute('href'))).toEqual([
      '/awards/10',
      '/awards/22',
      '/awards/31',
    ]);
    expect(texts(element, '[data-testid="timeline-stop-year"]')).toEqual(['2024', '2026', '2026']);
    expect(texts(element, '[data-testid="timeline-stop-title"]')).toEqual([
      'Faculty diploma',
      'Подяка кафедри',
      'National grant',
    ]);
    expect(texts(element, '[data-testid="timeline-stop-level"]')).toEqual([
      'Faculty',
      'Department',
      'National',
    ]);
    expect(element.querySelector('[data-testid="timeline-start"]')?.textContent).toContain('2024');
    expect(element.querySelector('[data-testid="timeline-today"]')?.textContent).toContain('today');
  });

  it('ac3_2_turns_the_road_back_on_every_second_row', () => {
    const element = render(true, entries(6));
    const stops = [...element.querySelectorAll<HTMLElement>('[data-testid="timeline-stop"]')].map(
      (stop) => stop.parentElement as HTMLElement,
    );

    expect(stops.map((stop) => stop.style.gridRow)).toEqual(['1', '1', '1', '1', '2', '2']);
    expect(stops.map((stop) => stop.style.gridColumn)).toEqual(['1', '2', '3', '4', '4', '3']);
    expect(element.querySelectorAll('.road__lane--back')).toHaveLength(1);
    expect(element.querySelectorAll('.road__lane--turn')).toHaveLength(1);
  });

  it('ac3_2_marks_verified_milestones_and_names_the_issuer_on_hover', () => {
    const element = render(true);
    const stops = [...element.querySelectorAll<HTMLElement>('[data-testid="timeline-stop"]')];

    expect(
      stops.map((stop) => !!stop.querySelector('[data-testid="timeline-stop-verified"]')),
    ).toEqual([true, false, true]);
    expect(stops[2].getAttribute('title')).toBe('Ministry of Education · Grant');
    expect(element.querySelector('ol')?.getAttribute('aria-label')).toBe(
      'My journey from the first award to today',
    );
  });

  it('ac3_2_says_so_when_there_is_no_award_at_any_width', () => {
    const wide = render(true, []);

    expect(wide.querySelector('[data-testid="timeline-road"]')).toBeNull();
    expect(wide.querySelector('[data-testid="timeline-none"]')?.textContent).toContain(
      'No approved awards yet.',
    );
  });

  it('ac3_2_shows_one_milestone_for_a_single_award', () => {
    const element = render(true, sampleAnalytics().timeline.slice(0, 1));

    expect(element.querySelectorAll('[data-testid="timeline-stop"]')).toHaveLength(1);
    expect(element.querySelectorAll('.road__lane')).toHaveLength(1);
  });

  it('ac3_3_groups_the_feed_by_year_with_the_count_below_960_px', () => {
    const element = render(false);
    const rows = [...element.querySelectorAll<HTMLElement>('[data-testid="timeline-row"]')];

    expect(element.querySelector('[data-testid="timeline-road"]')).toBeNull();
    expect(texts(element, '[data-testid="timeline-year"]')).toEqual([
      '2026 2 awards',
      '2024 1 award',
    ]);
    expect(rows.map((row) => row.getAttribute('href'))).toEqual([
      '/awards/31',
      '/awards/22',
      '/awards/10',
    ]);
    expect(texts(element, '[data-testid="timeline-day"]')).toEqual(['01 Mar', '15 Jan', '10 May']);
    expect(rows[0].textContent).toContain('verified');
    expect(rows[1].textContent).not.toContain('verified');
    expect(rows[0].textContent).toContain('Ministry of Education · Grant');
    expect(texts(element, '[data-testid="timeline-row-level"]')).toEqual([
      'National',
      'Department',
      'Faculty',
    ]);
  });

  it('ac3_3_filters_the_feed_to_national_and_higher_awards', () => {
    const element = render(false);
    const national = element.querySelector<HTMLButtonElement>(
      '[data-testid="timeline-filter-national"]',
    );

    national?.click();
    fixture.detectChanges();

    expect(national?.getAttribute('aria-pressed')).toBe('true');
    expect(
      [...element.querySelectorAll('[data-testid="timeline-row"]')].map((row) =>
        row.getAttribute('href'),
      ),
    ).toEqual(['/awards/31']);
    expect(texts(element, '[data-testid="timeline-year"]')).toEqual(['2026 1 award']);

    element.querySelector<HTMLButtonElement>('[data-testid="timeline-filter-all"]')?.click();
    fixture.detectChanges();

    expect(element.querySelectorAll('[data-testid="timeline-row"]')).toHaveLength(3);
  });

  it('ac3_3_says_so_when_no_award_reaches_the_national_level', () => {
    const element = render(false, sampleAnalytics().timeline.slice(1));

    element.querySelector<HTMLButtonElement>('[data-testid="timeline-filter-national"]')?.click();
    fixture.detectChanges();

    expect(element.querySelector('[data-testid="timeline-none"]')?.textContent).toContain(
      'No national or international awards',
    );
  });

  it('ac3_4_links_to_all_awards_when_only_the_newest_are_shown', () => {
    const element = render(true);
    expect(element.querySelector('[data-testid="timeline-truncated"]')).toBeNull();

    fixture.componentRef.setInput('truncated', true);
    fixture.detectChanges();

    expect(element.querySelector('[data-testid="timeline-truncated"]')?.textContent).toContain(
      'newest 3 awards',
    );
    expect(element.querySelector('[data-testid="timeline-all"]')?.getAttribute('href')).toBe(
      '/awards',
    );
    expect(element.querySelector('[data-testid="timeline-start"]')).toBeNull();
    expect(element.querySelector('ol')?.getAttribute('aria-label')).toBe(
      'My newest awards up to today',
    );
  });
});
