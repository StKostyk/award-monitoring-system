import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import { LanguageService } from '../../../core/i18n/language.service';
import { DuplicateDialogComponent } from './duplicate-dialog.component';

describe('DuplicateDialogComponent', () => {
  it('ac2_6_links_every_matching_award_and_offers_both_answers', async () => {
    await TestBed.configureTestingModule({
      imports: [
        DuplicateDialogComponent,
        TranslocoTestingModule.forRoot({
          langs: { uk: {} },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideRouter([]),
        { provide: LanguageService, useValue: { current: () => 'en' } },
        { provide: MatDialogRef, useValue: { close: vi.fn() } },
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            matches: [
              {
                id: 9,
                title: 'Ministry letter',
                titleUk: 'Грамота МОН',
                awardDate: '2025-05-01',
                status: 'PENDING',
              },
              { id: 11, title: null, titleUk: 'Грамота', awardDate: '2025-05-01', status: 'DRAFT' },
            ],
          },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(DuplicateDialogComponent);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('[data-testid="duplicate-match-9"]')?.textContent).toContain(
      'Ministry letter',
    );
    expect(element.querySelector('[data-testid="duplicate-match-9"]')?.getAttribute('href')).toBe(
      '/awards/9',
    );
    expect(element.querySelector('[data-testid="duplicate-match-11"]')?.textContent).toContain(
      'Грамота',
    );
    expect(element.textContent).toContain('01.05.2025');
    expect(element.querySelector('[data-testid="duplicate-confirm"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="duplicate-cancel"]')).not.toBeNull();
  });
});
