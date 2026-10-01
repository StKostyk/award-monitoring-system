import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';

import { LanguageService } from '../../../core/i18n/language.service';
import { AwardRequestSummary } from '../awards.service';
import { RequestTimingComponent } from './request-timing.component';

const submitted: AwardRequestSummary = {
  status: 'SUBMITTED',
  currentLevel: 'DEAN',
  submittedAt: '2026-09-28T09:00:00Z',
  deadline: '2026-10-01T09:00:00Z',
  estimatedCompletion: '2026-10-07',
  overdue: false,
};

describe('RequestTimingComponent', () => {
  let fixture: ComponentFixture<RequestTimingComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        RequestTimingComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            uk: {
              awards: {
                statusPanel: { returned: 'Очікує ваших виправлень' },
                timing: { expected: 'Очікується до {{date}}', delayed: 'Затримка' },
              },
              roles: { DEAN: 'Декан' },
            },
          },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [{ provide: LanguageService, useValue: { current: () => 'uk' } }],
    }).compileComponents();
    fixture = TestBed.createComponent(RequestTimingComponent);
  });

  function render(request: AwardRequestSummary, showLevel = false): HTMLElement {
    fixture.componentRef.setInput('request', request);
    fixture.componentRef.setInput('showLevel', showLevel);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function timing(element: HTMLElement): string {
    return element.querySelector('[data-testid="request-timing"]')?.textContent?.trim() ?? '';
  }

  it('ac1_18_names_the_expected_date_and_optionally_the_level', () => {
    expect(timing(render(submitted))).toBe('Очікується до 07.10.2026');
    expect(timing(render(submitted, true)).replace(/\s+/g, ' ')).toBe(
      'Декан · Очікується до 07.10.2026',
    );
  });

  it('ac1_17_a_returned_request_waits_for_the_owner_without_level_or_date', () => {
    const element = render({ ...submitted, status: 'RETURNED' }, true);

    expect(timing(element)).toBe('Очікує ваших виправлень');
  });

  it('ac1_18_marks_an_overdue_request_with_a_chip', () => {
    expect(render(submitted).querySelector('[data-testid="request-delayed"]')).toBeNull();
    expect(
      render({ ...submitted, overdue: true }).querySelector('[data-testid="request-delayed"]')
        ?.textContent,
    ).toContain('Затримка');
  });

  it('edge_shows_nothing_without_an_estimate_or_level', () => {
    const element = render({ ...submitted, estimatedCompletion: null });

    expect(element.querySelector('[data-testid="request-timing"]')).toBeNull();
  });
});
