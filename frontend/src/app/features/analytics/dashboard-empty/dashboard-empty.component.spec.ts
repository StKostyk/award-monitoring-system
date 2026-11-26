import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { translations } from '../analytics.testing';
import { DashboardEmptyComponent } from './dashboard-empty.component';

describe('DashboardEmptyComponent', () => {
  let fixture: ComponentFixture<DashboardEmptyComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [DashboardEmptyComponent, translations('en')],
      providers: [provideRouter([])],
    });
    fixture = TestBed.createComponent(DashboardEmptyComponent);
  });

  function element(testId: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  it('ac2_8_lists_the_steps_to_a_first_award_with_the_entry_button', () => {
    fixture.componentRef.setInput('canCreate', true);
    fixture.detectChanges();

    expect(element('dashboard-empty-first')?.querySelectorAll('li')).toHaveLength(3);
    expect(element('empty-new-award')?.getAttribute('href')).toBe('/awards/new');
    expect(element('empty-in-progress')).toBeNull();
  });

  it('ac2_8_counts_the_awards_in_review_and_drafts_and_links_to_my_awards', () => {
    fixture.componentRef.setInput('pending', 2);
    fixture.componentRef.setInput('drafts', 1);
    fixture.detectChanges();

    const progress = element('empty-in-progress')?.textContent;

    expect(progress).toContain('Awards in review: 2.');
    expect(progress).toContain('Drafts: 1.');
    expect(element('empty-awards-link')?.getAttribute('href')).toBe('/awards');
    expect(element('empty-new-award')).toBeNull();
  });

  it('ac2_8_offers_all_years_when_only_the_period_is_empty', () => {
    let asked = 0;
    fixture.componentInstance.allYears.subscribe(() => asked++);
    fixture.componentRef.setInput('periodOnly', true);
    fixture.detectChanges();

    expect(element('dashboard-empty-period')?.textContent).toContain(
      'No approved awards in this period',
    );
    element('empty-all-years')?.click();

    expect(asked).toBe(1);
    expect(element('dashboard-empty-first')).toBeNull();
  });
});
