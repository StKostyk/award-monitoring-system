import { Component, DestroyRef, inject, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { Subject } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../auth/auth.service';
import { CommandPaletteService } from './command-palette.service';
import { CommandPaletteComponent } from './command-palette/command-palette.component';

@Component({ selector: 'app-host', template: '' })
class HostComponent {
  constructor() {
    inject(CommandPaletteService).listen(inject(DestroyRef));
  }
}

describe('CommandPaletteService', () => {
  const closed = new Subject<void>();
  const dialog = {
    openDialogs: [] as unknown[],
    open: vi.fn(() => ({ afterClosed: () => closed })),
  };
  const auth = { isAuthenticated: signal(true) };

  function press(init: KeyboardEventInit): KeyboardEvent {
    const event = new KeyboardEvent('keydown', { cancelable: true, ...init });
    document.dispatchEvent(event);
    return event;
  }

  beforeEach(() => {
    dialog.open.mockClear();
    dialog.openDialogs = [];
    auth.isAuthenticated.set(true);
    TestBed.configureTestingModule({
      providers: [
        { provide: MatDialog, useValue: dialog },
        { provide: AuthService, useValue: auth },
      ],
    });
  });

  it('ac3_4_opens_the_palette_on_ctrl_k_outside_apple_devices', async () => {
    const host = TestBed.createComponent(HostComponent);

    const meta = press({ key: 'k', code: 'KeyK', metaKey: true });
    const ctrl = press({ key: 'k', code: 'KeyK', ctrlKey: true });
    await vi.waitFor(() => expect(dialog.open).toHaveBeenCalledTimes(1));

    expect(meta.defaultPrevented).toBe(false);
    expect(ctrl.defaultPrevented).toBe(true);
    expect(dialog.open).toHaveBeenCalledWith(
      CommandPaletteComponent,
      expect.objectContaining({ restoreFocus: true }),
    );
    host.destroy();
  });

  it('ac3_4_opens_on_the_k_key_of_a_ukrainian_layout', async () => {
    const host = TestBed.createComponent(HostComponent);

    press({ key: 'л', code: 'KeyK', ctrlKey: true });

    await vi.waitFor(() => expect(dialog.open).toHaveBeenCalledTimes(1));
    host.destroy();
  });

  it('ac3_4_ignores_k_without_a_modifier_and_stops_listening_when_destroyed', () => {
    const host = TestBed.createComponent(HostComponent);

    press({ code: 'KeyA', ctrlKey: true });
    press({ key: 'k', code: 'KeyK' });
    press({ key: 'k', code: 'KeyK', ctrlKey: true, shiftKey: true });
    host.destroy();
    press({ key: 'k', code: 'KeyK', ctrlKey: true });

    expect(dialog.open).not.toHaveBeenCalled();
  });

  it('ac3_4_opens_no_second_palette_while_one_or_another_dialog_is_open', async () => {
    const palette = TestBed.inject(CommandPaletteService);

    await Promise.all([palette.open(), palette.open()]);
    await palette.open();
    expect(dialog.open).toHaveBeenCalledTimes(1);

    closed.next();
    dialog.openDialogs = [{}];
    await palette.open();

    expect(dialog.open).toHaveBeenCalledTimes(1);
  });

  it('ac3_4_opens_nothing_for_an_anonymous_visitor_and_leaves_the_browser_shortcut_alone', async () => {
    auth.isAuthenticated.set(false);
    const host = TestBed.createComponent(HostComponent);

    const event = press({ key: 'k', code: 'KeyK', ctrlKey: true });
    await TestBed.inject(CommandPaletteService).open();

    expect(event.defaultPrevented).toBe(false);
    expect(dialog.open).not.toHaveBeenCalled();
    host.destroy();
  });

  it('ac3_4_leaves_ctrl_k_to_an_open_dialog', () => {
    const host = TestBed.createComponent(HostComponent);
    const other = document.body.appendChild(document.createElement('mat-dialog-container'));

    const event = press({ key: 'k', code: 'KeyK', ctrlKey: true });

    expect(event.defaultPrevented).toBe(false);
    other.remove();
    host.destroy();
  });
});
