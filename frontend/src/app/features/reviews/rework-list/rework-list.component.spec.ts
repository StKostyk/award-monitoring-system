import { BreakpointObserver } from '@angular/cdk/layout';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import {
  ActivatedRoute,
  ParamMap,
  Router,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { LanguageService } from '../../../core/i18n/language.service';
import { Page } from '../../awards/awards.service';
import { ReturnedItem, ReviewsService } from '../reviews.service';
import { ReworkListComponent } from './rework-list.component';

function returnedItem(overrides: Partial<ReturnedItem> = {}): ReturnedItem {
  return {
    awardId: 5,
    requestId: 8,
    title: null,
    titleUk: 'Грамота Міністерства освіти і науки',
    recipient: { type: 'PERSON', organization: null },
    owner: { id: 21, name: 'Анастасія Коваль', email: 'employee.fmi@chnu.edu.ua' },
    organization: {
      id: 64,
      name: 'Algebra',
      nameUk: 'Кафедра алгебри',
      code: 'DAI',
      type: 'DEPARTMENT',
    },
    category: null,
    level: 'FACULTY_SECRETARY',
    returnedAt: '2026-11-17T09:00:00Z',
    returnedBy: { id: 22, name: 'Олена Мельник', email: 'secretary.fmi@chnu.edu.ua' },
    returnedFor: null,
    comment: 'Додайте скан наказу',
    daysWaiting: 3,
    ...overrides,
  };
}

function page(items: ReturnedItem[], total = items.length): Page<ReturnedItem> {
  return { content: items, totalElements: total, totalPages: 1, size: 20, number: 0 };
}

const translations = {
  uk: {
    reviews: {
      rework: {
        empty: 'Немає нагород на доопрацюванні',
        days: '{{count}} дн.',
        onBehalfOf: 'за {{name}}',
        loadFailed: 'Не вдалося завантажити список.',
      },
    },
  },
};

describe('ReworkListComponent', () => {
  let fixture: ComponentFixture<ReworkListComponent>;
  const query = new BehaviorSubject<ParamMap>(convertToParamMap({}));
  const wide = new BehaviorSubject({ matches: true, breakpoints: {} });
  const reviews = { returned: vi.fn() };

  async function create(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [
        ReworkListComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: query,
            get snapshot() {
              return { queryParamMap: query.value };
            },
          },
        },
        { provide: BreakpointObserver, useValue: { observe: () => wide } },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        { provide: ReviewsService, useValue: reviews },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ReworkListComponent);
    fixture.detectChanges();
  }

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function text(testId: string): string {
    return element().querySelector(`[data-testid="${testId}"]`)?.textContent?.trim() ?? '';
  }

  beforeEach(() => {
    query.next(convertToParamMap({}));
    wide.next({ matches: true, breakpoints: {} });
    reviews.returned.mockReset();
  });

  it('9_2_9_ac1_lists_who_sent_the_award_back_and_the_days_and_shows_the_first_in_the_pane', async () => {
    reviews.returned.mockReturnValue(of(page([returnedItem()])));
    await create();

    expect(reviews.returned).toHaveBeenCalledWith(0, 20);
    expect(element().querySelectorAll('[data-testid="rework-item"]')).toHaveLength(1);
    expect(text('rework-days')).toBe('3 дн.');
    expect(text('rework-returned-by')).toBe('Олена Мельник');
    expect(text('rework-card-person')).toBe('Анастасія Коваль');
    expect(text('rework-card-reason')).toBe('Додайте скан наказу');
    expect(text('rework-card-days')).toBe('3 дн.');
  });

  it('9_2_9_ac3_a_delegated_return_names_whose_role_was_used_and_nothing_can_be_decided', async () => {
    reviews.returned.mockReturnValue(
      of(page([returnedItem({ returnedFor: { id: 30, name: 'Петро Дзюба', email: 'd@x' } })])),
    );
    await create();

    expect(text('rework-card-returned-by')).toContain('за Петро Дзюба');
    expect(element().querySelector('[data-testid="rework-card"] button')).toBeNull();
  });

  it('9_2_9_ac3_a_row_click_keeps_the_award_in_the_url', async () => {
    reviews.returned.mockReturnValue(
      of(page([returnedItem(), returnedItem({ awardId: 6, requestId: 9 })])),
    );
    await create();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    element().querySelectorAll<HTMLElement>('[data-testid="rework-item"]')[1].click();

    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { returned: 6 }, queryParamsHandling: 'merge' }),
    );
  });

  it('9_2_9_ac4_shows_the_empty_state_and_emits_the_count', async () => {
    reviews.returned.mockReturnValue(of(page([])));
    await create();
    const totals: number[] = [];
    fixture.componentInstance.totalChange.subscribe((total) => totals.push(total));
    fixture.componentInstance.load();

    expect(text('rework-empty')).toBe('Немає нагород на доопрацюванні');
    expect(element().querySelector('[data-testid="rework-paginator"]')).toBeNull();
    expect(totals).toEqual([0]);
  });

  it('9_2_9_ac4_pages_through_the_tab', async () => {
    reviews.returned.mockReturnValue(of(page([returnedItem()], 45)));
    await create();

    fixture.componentInstance.page({ pageIndex: 2, pageSize: 20, length: 45 });

    expect(reviews.returned).toHaveBeenLastCalledWith(2, 20);
  });

  it('edge_below_960_px_the_pane_waits_for_an_opened_award_and_a_failed_read_says_so', async () => {
    wide.next({ matches: false, breakpoints: {} });
    reviews.returned.mockReturnValue(of(page([returnedItem()])));
    await create();

    expect(element().querySelector('[data-testid="rework-card"]')).toBeNull();
    query.next(convertToParamMap({ returned: '5' }));
    fixture.detectChanges();
    expect(element().querySelector('[data-testid="rework-card"]')).not.toBeNull();

    reviews.returned.mockReturnValue(throwError(() => new Error('offline')));
    fixture.componentInstance.load();
    fixture.detectChanges();
    expect(text('rework-error')).toContain('Не вдалося завантажити список.');
  });
});
