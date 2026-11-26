import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { readPermissions } from '../../../core/auth/permissions';
import { ReviewPeriod, ReviewsService } from '../reviews.service';
import { ReviewPeriodComponent } from './review-period.component';

const translations = {
  uk: {
    reviews: {
      period: {
        label: 'Термін розгляду: {{days}}',
        days: {
          one: '{{count}} робочий день',
          few: '{{count}} робочі дні',
          many: '{{count}} робочих днів',
          other: '{{count}} робочого дня',
        },
        default: '(типовий)',
        change: 'Змінити',
      },
    },
  },
};

function token(claims: Record<string, unknown>): string {
  return `header.${btoa(JSON.stringify(claims))}.signature`;
}

function period(changes: Partial<ReviewPeriod> = {}): ReviewPeriod {
  return {
    organizationId: 9,
    workingDays: null,
    effectiveWorkingDays: 3,
    defaultWorkingDays: 3,
    updatable: true,
    ...changes,
  };
}

describe('ReviewPeriodComponent', () => {
  let fixture: ComponentFixture<ReviewPeriodComponent>;
  const reviewPeriod = vi.fn();
  const open = vi.fn();

  async function create(scopes: string[]): Promise<HTMLElement> {
    await TestBed.configureTestingModule({
      imports: [
        ReviewPeriodComponent,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        {
          provide: AuthService,
          useValue: { permissions: signal(readPermissions(token({ role_scopes: scopes }))) },
        },
        { provide: ReviewsService, useValue: { reviewPeriod } },
        { provide: MatDialog, useValue: { open } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ReviewPeriodComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function text(element: HTMLElement): string {
    return (
      element
        .querySelector('[data-testid="review-period-text"]')
        ?.textContent?.replace(/\s+/g, ' ')
        .trim() ?? ''
    );
  }

  beforeEach(() => {
    reviewPeriod.mockReset();
    open.mockReset();
  });

  it('ac1_1_shows_the_default_period_with_change_for_the_dean', async () => {
    reviewPeriod.mockReturnValue(of(period()));
    const element = await create(['DEAN:9']);

    expect(reviewPeriod).toHaveBeenCalledWith(9);
    expect(text(element)).toBe('Термін розгляду: 3 робочі дні (типовий)');
    expect(element.querySelector('[data-testid="review-period-change"]')).not.toBeNull();
  });

  it('ac1_2_shows_the_own_period_in_the_right_form_and_updates_after_the_dialog', async () => {
    reviewPeriod.mockReturnValue(of(period()));
    open.mockReturnValue({
      afterClosed: () => of(period({ workingDays: 5, effectiveWorkingDays: 5 })),
    });
    const element = await create(['DEAN:9']);

    element.querySelector<HTMLButtonElement>('[data-testid="review-period-change"]')?.click();
    fixture.detectChanges();

    expect(open).toHaveBeenCalled();
    expect(text(element)).toBe('Термін розгляду: 5 робочих днів');
  });

  it('ac1_5_shows_the_period_without_change_to_a_faculty_secretary', async () => {
    reviewPeriod.mockReturnValue(
      of(period({ workingDays: 1, effectiveWorkingDays: 1, updatable: false })),
    );
    const element = await create(['FACULTY_SECRETARY:9']);

    expect(text(element)).toBe('Термін розгляду: 1 робочий день');
    expect(element.querySelector('[data-testid="review-period-change"]')).toBeNull();
  });

  it('edge_shows_nothing_for_rector_levels_or_an_unreadable_scope', async () => {
    reviewPeriod.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    const element = await create(['RECTOR:1', 'FACULTY_SECRETARY:64']);

    expect(reviewPeriod).toHaveBeenCalledTimes(1);
    expect(element.querySelector('[data-testid="review-period"]')).toBeNull();
  });
});
