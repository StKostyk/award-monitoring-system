import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { CountUpDirective } from './count-up.directive';
import { MotionHelper } from './motion.helper';
import { StaggerDirective } from './stagger.directive';

@Component({
  imports: [CountUpDirective, StaggerDirective],
  template: `
    <ul appStagger>
      <li [appCountUp]="value()"></li>
      <li>second</li>
    </ul>
  `,
})
class HostComponent {
  readonly value = signal(5);
}

describe('CountUpDirective and StaggerDirective', () => {
  const stop = vi.fn();
  const helper = {
    countUp: vi.fn((element: HTMLElement, _from: number, to: number) => {
      element.textContent = String(to);
      return { stop };
    }),
    stagger: vi.fn(),
  };

  beforeEach(() => {
    stop.mockReset();
    helper.countUp.mockClear();
    helper.stagger.mockClear();
    TestBed.configureTestingModule({ providers: [{ provide: MotionHelper, useValue: helper }] });
  });

  it('ac2_3_counts_from_zero_then_from_the_number_on_screen', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const item = fixture.nativeElement.querySelector('li');

    expect(helper.countUp).toHaveBeenCalledWith(item, 0, 5, expect.anything());
    fixture.componentInstance.value.set(9);
    fixture.detectChanges();

    expect(stop).toHaveBeenCalledTimes(1);
    expect(helper.countUp).toHaveBeenLastCalledWith(item, 5, 9, expect.anything());
  });

  it('ac2_3_staggers_the_child_entries_after_the_first_render', async () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    const entries = Array.from(fixture.nativeElement.querySelectorAll('li'));

    expect(helper.stagger).toHaveBeenCalledWith(entries, expect.anything());
  });
});
