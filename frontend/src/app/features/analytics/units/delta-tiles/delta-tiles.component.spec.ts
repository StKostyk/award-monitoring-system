import { ComponentFixture, TestBed } from '@angular/core/testing';

import { languageStub, translations } from '../../analytics.testing';
import { facultyAnalytics } from '../unit-analytics.testing';
import { DeltaTilesComponent } from './delta-tiles.component';

describe('DeltaTilesComponent', () => {
  let fixture: ComponentFixture<DeltaTilesComponent>;

  function render(inputs: Record<string, unknown> = {}): HTMLElement {
    TestBed.configureTestingModule({
      imports: [DeltaTilesComponent, translations('en')],
      providers: [languageStub('en').provider],
    });
    fixture = TestBed.createComponent(DeltaTilesComponent);
    fixture.componentRef.setInput('totals', facultyAnalytics().totals);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function delta(element: HTMLElement, tile: string): HTMLElement {
    return element.querySelector(`[data-testid="${tile}"] [data-testid="delta"]`) as HTMLElement;
  }

  it('ac2_3_shows_the_change_with_an_arrow_in_the_grid_layout', () => {
    const element = render();

    expect(element.classList).not.toContain('tiles-bento');
    expect(delta(element, 'tile-approved').classList).toContain('delta--up');
    expect(delta(element, 'tile-approved').textContent).toContain('+5 (+50 %)');
    expect(delta(element, 'tile-awarded-people').textContent).toContain('new');
    expect(element.querySelector('[data-testid="tile-per-hundred"]')!.textContent).toContain(
      '37.5',
    );
    expect(element.querySelector('[data-testid="tile-spark"]')).toBeNull();
  });

  it('ac4_1_shows_a_detailed_chip_and_the_sparkline_on_the_bento_tiles', () => {
    const element = render({ layout: 'bento', years: [3, 5, 4] });

    expect(element.classList).toContain('tiles-bento');
    expect(delta(element, 'tile-approved').classList).toContain('delta-chip');
    expect(delta(element, 'tile-approved').textContent).toContain('+5 · +50 %');
    expect(delta(element, 'tile-awarded-people').textContent).toContain('new');
    const spark = element.querySelectorAll('[data-testid="tile-spark"]');
    expect(spark).toHaveLength(1);
    expect(spark[0].closest('[data-testid="tile-approved"]')).not.toBeNull();
    expect(spark[0].querySelector('polyline')!.getAttribute('points')).toBeTruthy();
  });

  it('ac4_1_draws_no_sparkline_from_a_single_year', () => {
    const element = render({ layout: 'bento', years: [4] });

    expect(element.querySelector('[data-testid="tile-spark"]')).toBeNull();
  });

  it('ac2_3_shows_a_dash_without_members', () => {
    TestBed.configureTestingModule({
      imports: [DeltaTilesComponent, translations('en')],
      providers: [languageStub('en').provider],
    });
    fixture = TestBed.createComponent(DeltaTilesComponent);
    fixture.componentRef.setInput('totals', {
      ...facultyAnalytics().totals,
      perHundredMembers: null,
    });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="tile-per-hundred"] .tile__value')
        .textContent,
    ).toContain('—');
  });

  it('ac4_2_captions_each_change_with_the_previous_period', () => {
    const element = render({ previousPeriod: { from: 2017, to: 2021 } });
    const captions = [...element.querySelectorAll('[data-testid="tile-against"]')];

    expect(captions.map((caption) => caption.textContent!.trim())).toEqual([
      'vs 2017–2021',
      'vs 2017–2021',
    ]);
    expect(captions[0].closest('[data-testid="tile-approved"]')).not.toBeNull();
    fixture.componentRef.setInput('previousPeriod', null);
    fixture.detectChanges();
    expect(element.querySelector('[data-testid="tile-against"]')).toBeNull();
  });
});
