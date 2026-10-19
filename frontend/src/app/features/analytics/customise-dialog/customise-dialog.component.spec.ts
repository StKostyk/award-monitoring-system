import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { vi } from 'vitest';

import { translations } from '../analytics.testing';
import { CustomiseDialogComponent } from './customise-dialog.component';

describe('CustomiseDialogComponent', () => {
  it('ac2_9_closes_with_the_unticked_widgets_in_display_order', () => {
    const ref = { close: vi.fn() };
    TestBed.configureTestingModule({
      imports: [CustomiseDialogComponent, NoopAnimationsModule, translations('en')],
      providers: [
        { provide: MatDialogRef, useValue: ref },
        { provide: MAT_DIALOG_DATA, useValue: ['RANK'] },
      ],
    });
    const fixture = TestBed.createComponent(CustomiseDialogComponent);
    fixture.detectChanges();
    const box = (widget: string): HTMLInputElement =>
      fixture.nativeElement.querySelector(`[data-testid="customise-${widget}"] input`);

    expect(box('RANK').checked).toBe(false);
    expect(box('SUMMARY').checked).toBe(true);

    box('TIMELINE').click();
    box('RANK').click();
    box('SUMMARY').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="customise-save"]').click();

    expect(ref.close).toHaveBeenCalledWith(['SUMMARY', 'TIMELINE']);
  });
});
