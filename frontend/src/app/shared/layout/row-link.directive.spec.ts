import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, RouterLink, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { RowLinkDirective } from './row-link.directive';

@Component({
  imports: [RouterLink, RowLinkDirective],
  template: `
    <table>
      <tbody>
        <tr [appRowLink]="active()" data-testid="row">
          <th><a routerLink="/admin/users/7" data-testid="link">Name</a></th>
          <td data-testid="cell">Unit</td>
          <td>
            <button type="button" (click)="pressed = pressed + 1" data-testid="button">Act</button>
          </td>
        </tr>
      </tbody>
    </table>
  `,
})
class HostComponent {
  readonly active = signal(true);
  pressed = 0;
}

describe('RowLinkDirective', () => {
  let fixture: ComponentFixture<HostComponent>;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    window.getSelection()?.removeAllRanges();
  });

  function element(testId: string): HTMLElement {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  function click(testId: string, init: MouseEventInit = {}, type = 'click'): void {
    element(testId).dispatchEvent(
      new MouseEvent(type, { bubbles: true, cancelable: true, ...init }),
    );
  }

  it('ac1_click_on_a_cell_opens_the_row_link', () => {
    click('cell');

    expect(navigate).toHaveBeenCalledTimes(1);
    expect(String(navigate.mock.calls[0][0])).toBe('/admin/users/7');
  });

  it('ac1_click_on_the_link_navigates_once', () => {
    click('link', { button: 0 });

    expect(navigate).toHaveBeenCalledTimes(1);
  });

  it('ac1_button_in_the_row_keeps_its_own_action', () => {
    click('button');

    expect(fixture.componentInstance.pressed).toBe(1);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('ac1_ctrl_click_and_middle_click_open_a_new_tab', () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null);

    click('cell', { ctrlKey: true });
    click('cell', { shiftKey: true });
    click('cell', { button: 1 }, 'auxclick');
    click('cell', { button: 2 }, 'auxclick');

    expect(open).toHaveBeenCalledTimes(3);
    expect(open.mock.calls[0][0]).toMatch(/\/admin\/users\/7$/);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('ac1_middle_button_press_on_a_cell_does_not_start_autoscroll', () => {
    const press = new MouseEvent('mousedown', { bubbles: true, cancelable: true, button: 1 });
    const onButton = new MouseEvent('mousedown', { bubbles: true, cancelable: true, button: 1 });

    element('cell').dispatchEvent(press);
    element('button').dispatchEvent(onButton);

    expect(press.defaultPrevented).toBe(true);
    expect(onButton.defaultPrevented).toBe(false);
  });

  it('ac1_click_that_ends_a_text_selection_does_not_navigate', () => {
    const range = document.createRange();
    range.selectNodeContents(element('cell'));
    window.getSelection()?.addRange(range);

    click('cell');

    expect(navigate).not.toHaveBeenCalled();
  });

  it('ac2_marks_the_row_for_the_pointer_and_hover_style_only_while_active', () => {
    expect(element('row').classList).toContain('app-row-link');

    fixture.componentInstance.active.set(false);
    fixture.detectChanges();
    click('cell');

    expect(element('row').classList).not.toContain('app-row-link');
    expect(navigate).not.toHaveBeenCalled();
  });
});
