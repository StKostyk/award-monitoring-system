import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { SkeletonRowsComponent } from '../skeleton/skeleton-rows.component';
import { EmptyStateComponent } from './empty-state.component';

@Component({
  imports: [EmptyStateComponent, SkeletonRowsComponent],
  template: `
    <app-empty-state icon="inbox" text="No awards yet.">
      <button type="button">Add an award</button>
    </app-empty-state>
    <app-skeleton-rows [count]="3" label="Loading" />
  `,
})
class HostComponent {}

describe('EmptyStateComponent and SkeletonRowsComponent', () => {
  function render(): HTMLElement {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('ac4_empty_state_shows_an_icon_one_sentence_and_the_projected_action', () => {
    const element = render().querySelector('app-empty-state') as HTMLElement;

    expect(element.querySelector('.empty-state__icon app-icon')).not.toBeNull();
    expect(element.querySelector('.empty-state__icon')?.getAttribute('aria-hidden')).toBe('true');
    expect(element.querySelector('p')?.textContent).toBe('No awards yet.');
    expect(element.querySelector('button')?.textContent).toBe('Add an award');
  });

  it('ac5_skeleton_shows_the_rows_in_a_named_status_region', () => {
    const element = render().querySelector('app-skeleton-rows') as HTMLElement;

    expect(element.getAttribute('role')).toBe('status');
    expect(element.getAttribute('aria-label')).toBe('Loading');
    const rows = element.querySelectorAll('[data-testid="skeleton-row"]');
    expect(rows.length).toBe(3);
    expect(rows[0].getAttribute('aria-hidden')).toBe('true');
  });
});
