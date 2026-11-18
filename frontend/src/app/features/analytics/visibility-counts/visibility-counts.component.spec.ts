import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { translations } from '../analytics.testing';
import { VisibilityCountsComponent } from './visibility-counts.component';

describe('VisibilityCountsComponent', () => {
  async function create(): Promise<HTMLElement> {
    await TestBed.configureTestingModule({
      imports: [VisibilityCountsComponent, translations('en')],
      providers: [provideRouter([])],
    }).compileComponents();
    const fixture = TestBed.createComponent(VisibilityCountsComponent);
    fixture.componentRef.setInput('counts', {
      publicAwards: 2,
      universityAwards: 1,
      privateAwards: 4,
      withDocument: 3,
    });
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('ac4_2_counts_each_visibility_and_links_to_the_filtered_list', async () => {
    const element = await create();
    const link = (visibility: string) =>
      element.querySelector(`[data-testid="visibility-${visibility}"]`);

    expect(link('PUBLIC')?.textContent).toContain('2');
    expect(link('UNIVERSITY')?.textContent).toContain('1');
    expect(link('PRIVATE')?.textContent).toContain('4');
    expect(link('PRIVATE')?.getAttribute('href')).toBe(
      '/awards?status=APPROVED&visibility=PRIVATE',
    );
    expect(element.querySelector('[data-testid="visibility-documents"]')?.textContent).toContain(
      '3',
    );
  });
});
