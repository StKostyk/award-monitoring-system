import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { ReviewPeriod, ReviewsService } from '../reviews.service';
import { ReviewPeriodDialogComponent } from './review-period-dialog.component';

const translations = {
  uk: {
    reviews: {
      period: {
        dialog: { range: 'Вкажіть ціле число від 1 до 20' },
        problems: { 'validation-failed': 'Вкажіть ціле число', unknown: 'Не вдалося зберегти' },
      },
    },
  },
};

const FACULTY_PERIOD = 5;

const current: ReviewPeriod = {
  organizationId: 9,
  workingDays: FACULTY_PERIOD,
  effectiveWorkingDays: FACULTY_PERIOD,
  defaultWorkingDays: 3,
  updatable: true,
};

describe('ReviewPeriodDialogComponent', () => {
  let fixture: ComponentFixture<ReviewPeriodDialogComponent>;
  const setReviewPeriod = vi.fn();
  const close = vi.fn();

  async function create(data: ReviewPeriod = current): Promise<HTMLElement> {
    await TestBed.configureTestingModule({
      imports: [
        ReviewPeriodDialogComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: { close } },
        { provide: ReviewsService, useValue: { setReviewPeriod } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ReviewPeriodDialogComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    setReviewPeriod.mockReset();
    close.mockReset();
  });

  it('ac1_2_saves_the_entered_period_and_closes_with_the_result', async () => {
    const saved = { ...current, workingDays: 7, effectiveWorkingDays: 7 };
    setReviewPeriod.mockReturnValue(of(saved));
    await create();

    fixture.componentInstance.days.setValue(7);
    fixture.componentInstance.submit();

    expect(setReviewPeriod).toHaveBeenCalledWith(9, 7);
    expect(close).toHaveBeenCalledWith(saved);
  });

  it('ac1_9_saves_with_enter_in_the_field', async () => {
    setReviewPeriod.mockReturnValue(of(current));
    const element = await create();

    element
      .querySelector<HTMLInputElement>('[data-testid="review-period-input"]')
      ?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));

    expect(setReviewPeriod).toHaveBeenCalledWith(9, FACULTY_PERIOD);
  });

  it('ac1_4_shows_the_range_error_after_save_with_an_empty_field', async () => {
    const element = await create();

    fixture.componentInstance.days.setValue(null);
    element.querySelector<HTMLButtonElement>('[data-testid="review-period-save"]')?.click();
    fixture.detectChanges();

    expect(setReviewPeriod).not.toHaveBeenCalled();
    expect(element.querySelector('[data-testid="review-period-range"]')).not.toBeNull();
  });

  it('ac1_3_restores_the_default_with_null', async () => {
    setReviewPeriod.mockReturnValue(of({ ...current, workingDays: null, effectiveWorkingDays: 3 }));
    const element = await create();

    element.querySelector<HTMLButtonElement>('[data-testid="review-period-default"]')?.click();

    expect(setReviewPeriod).toHaveBeenCalledWith(9, null);
  });

  it('ac1_3_offers_no_reset_when_the_default_already_applies', async () => {
    const element = await create({ ...current, workingDays: null, effectiveWorkingDays: 3 });

    expect(
      element.querySelector<HTMLButtonElement>('[data-testid="review-period-default"]')?.disabled,
    ).toBe(true);
  });

  it.each([0, 21, 5.5, null])('ac1_4_refuses_%s_without_a_request', async (value) => {
    const element = await create();

    fixture.componentInstance.days.setValue(value);
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(setReviewPeriod).not.toHaveBeenCalled();
    expect(element.querySelector('[data-testid="review-period-range"]')?.textContent).toContain(
      'Вкажіть ціле число від 1 до 20',
    );
  });

  it('ac1_4_shows_a_refusal_of_the_server_and_stays_open', async () => {
    setReviewPeriod.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 422,
            error: { type: 'urn:awards:problem:validation-failed' },
          }),
      ),
    );
    const element = await create();

    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(close).not.toHaveBeenCalled();
    expect(element.querySelector('[data-testid="review-period-error"]')?.textContent).toContain(
      'Вкажіть ціле число',
    );
    expect(fixture.componentInstance.busy()).toBe(false);
  });

  it('edge_maps_an_unexpected_problem_to_unknown', async () => {
    setReviewPeriod.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    await create();

    fixture.componentInstance.submit();

    expect(fixture.componentInstance.problem()).toBe('unknown');
  });
});
