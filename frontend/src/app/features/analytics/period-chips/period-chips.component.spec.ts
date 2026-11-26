import { ComponentFixture, TestBed } from '@angular/core/testing';

import { currentKyivYear } from '../../../shared/date-format';
import { AnalyticsPeriod } from '../analytics.service';
import { translations } from '../analytics.testing';
import { PeriodChipsComponent } from './period-chips.component';

describe('PeriodChipsComponent', () => {
  let fixture: ComponentFixture<PeriodChipsComponent>;

  function render(period: AnalyticsPeriod): void {
    TestBed.configureTestingModule({ imports: [PeriodChipsComponent, translations('uk')] });
    fixture = TestBed.createComponent(PeriodChipsComponent);
    fixture.componentRef.setInput('period', period);
    fixture.detectChanges();
  }

  function chip(testId: string): HTMLButtonElement {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  it('ac6_2_marks_the_whole_period_when_no_year_is_chosen', () => {
    render({ from: null, to: null });

    expect(chip('period-chip-all').getAttribute('aria-pressed')).toBe('true');
    expect(chip('period-chip-year').getAttribute('aria-pressed')).toBe('false');
    expect(chip('period-chip-year').textContent).toContain('Цей рік');
    expect(chip('period-chip-all').textContent).toContain('Увесь період');
  });

  it('ac6_2_marks_the_current_year_and_neither_for_another_range', () => {
    const year = currentKyivYear();
    render({ from: year, to: year });

    expect(chip('period-chip-year').getAttribute('aria-pressed')).toBe('true');

    fixture.componentRef.setInput('period', { from: 2021, to: 2025 });
    fixture.detectChanges();

    expect(chip('period-chip-year').getAttribute('aria-pressed')).toBe('false');
    expect(chip('period-chip-all').getAttribute('aria-pressed')).toBe('false');
  });

  it('ac6_2_chooses_the_current_year_or_the_whole_period', () => {
    render({ from: 2021, to: 2025 });
    const chosen: AnalyticsPeriod[] = [];
    let whole = 0;
    fixture.componentInstance.choose.subscribe((period) => chosen.push(period));
    fixture.componentInstance.allYears.subscribe(() => whole++);

    chip('period-chip-year').click();
    chip('period-chip-all').click();

    const year = currentKyivYear();
    expect(chosen).toEqual([{ from: year, to: year }]);
    expect(whole).toBe(1);
  });
});
