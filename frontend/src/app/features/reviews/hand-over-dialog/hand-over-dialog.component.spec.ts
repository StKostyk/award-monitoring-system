import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { ReviewsService } from '../reviews.service';
import { HandOverDialogComponent } from './hand-over-dialog.component';

const translations = {
  uk: {
    reviews: {
      handOver: { empty: 'Немає інших рецензентів', delegated: 'за дорученням' },
      problems: { network: 'Сервер недоступний.' },
    },
  },
};

describe('HandOverDialogComponent', () => {
  let fixture: ComponentFixture<HandOverDialogComponent>;
  const candidates = vi.fn();

  async function create(): Promise<HTMLElement> {
    await TestBed.configureTestingModule({
      imports: [
        HandOverDialogComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { awardId: 5 } },
        { provide: MatDialogRef, useValue: { close: vi.fn() } },
        { provide: ReviewsService, useValue: { candidates } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(HandOverDialogComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => candidates.mockReset());

  it('ac1_8_lists_the_candidates_with_the_delegated_flag_and_enables_the_choice', async () => {
    candidates.mockReturnValue(
      of([
        { id: 32, name: 'Олена Петрук', email: 'secretary2.fmi@chnu.edu.ua', delegated: false },
        { id: 40, name: 'Петро Заступник', email: 'deputy@chnu.edu.ua', delegated: true },
      ]),
    );
    const element = await create();
    const options = element.querySelectorAll('[data-testid="hand-over-candidate"]');
    const confirm = element.querySelector<HTMLButtonElement>('[data-testid="hand-over-confirm"]');

    expect(candidates).toHaveBeenCalledWith(5);
    expect(options.length).toBe(2);
    expect(options[1].textContent).toContain('за дорученням');
    expect(confirm?.disabled).toBe(true);

    options[0].querySelector<HTMLInputElement>('input')?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.componentInstance.selected()?.id).toBe(32);
    expect(confirm?.disabled).toBe(false);
  });

  it('ac1_8_says_when_nobody_else_can_take_the_request', async () => {
    candidates.mockReturnValue(of([]));

    const element = await create();

    expect(element.querySelector('[data-testid="hand-over-empty"]')).not.toBeNull();
  });

  it('ac1_8_reports_a_failed_load', async () => {
    candidates.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));

    const element = await create();

    expect(element.querySelector('[data-testid="hand-over-error"]')?.textContent).toContain(
      'Сервер недоступний.',
    );
  });
});
