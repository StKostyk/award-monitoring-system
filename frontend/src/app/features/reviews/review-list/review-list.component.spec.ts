import { BreakpointObserver } from '@angular/cdk/layout';
import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Store, provideState, provideStore } from '@ngrx/store';
import { BehaviorSubject, EMPTY, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { readPermissions } from '../../../core/auth/permissions';
import { OrganizationType } from '../../../core/auth/user-profile';
import { LanguageService } from '../../../core/i18n/language.service';
import {
  OrganizationSummary,
  OrganizationsService,
} from '../../../core/organizations/organizations.service';
import { BatchItemResult, NO_REVIEW_FILTERS, ReviewItem, ReviewsService } from '../reviews.service';
import { ReviewsActions } from '../store/reviews.actions';
import { reviewsFeature } from '../store/reviews.feature';
import { ReviewListComponent } from './review-list.component';

function reviewItem(overrides: Partial<ReviewItem> = {}): ReviewItem {
  return {
    awardId: 5,
    requestId: 8,
    requestVersion: 3,
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
    category: { id: 13, name: 'Ministry', nameUk: 'Відзнака міністерства', level: 'NATIONAL' },
    level: 'FACULTY_SECRETARY',
    status: 'SUBMITTED',
    reviewer: null,
    submittedAt: '2026-10-01T08:00:00Z',
    deadline: '2026-10-06T20:59:59Z',
    overdue: false,
    overdueNoticedAt: null,
    documentCount: 2,
    delegatedFrom: null,
    ...overrides,
  };
}

function token(claims: Record<string, unknown>): string {
  return `header.${btoa(JSON.stringify(claims))}.signature`;
}

function unit(id: number, type: OrganizationType, parent: number | null): OrganizationSummary {
  return {
    id,
    name: `Unit ${id}`,
    nameUk: `Підрозділ ${id}`,
    code: null,
    type,
    parent:
      parent === null ? null : { id: parent, name: '', nameUk: null, code: null, type: 'FACULTY' },
  };
}

const translations = {
  uk: {
    reviews: {
      title: 'На розгляді',
      tabs: { mine: 'Мої', unassigned: 'Нерозподілені', all: 'Усі' },
      overdue: 'Прострочено',
      noticed: 'Керівника повідомлено {{date}}',
      filters: { noticed: 'Повідомлені', lowerOverdue: 'Прострочені нижчого рівня' },
      empty: 'Немає нагород на розгляді',
      batch: {
        selected: 'Вибрано: {{count}}',
        approve: 'Затвердити ({{count}})',
        escalateTo: 'Передати {{level}}',
        escalate: 'Передати на вищий рівень',
        done: 'Опрацьовано: {{done}} з {{total}}',
        reasons: { 'request-claimed': 'Взято в роботу іншим рецензентом', unknown: 'Не вдалося' },
      },
      decide: { to: { DEAN: 'декану' } },
    },
    awards: { levels: { FACULTY_SECRETARY: 'секретар факультету', DEAN: 'декан' } },
    categories: { levels: { NATIONAL: 'Національний' } },
  },
};

describe('ReviewListComponent', () => {
  let fixture: ComponentFixture<ReviewListComponent>;
  let store: Store;
  const wide = new BehaviorSubject({ matches: true, breakpoints: {} });
  const permissions = signal(
    readPermissions(token({ role_scopes: ['EMPLOYEE:64', 'FACULTY_SECRETARY:9'] })),
  );
  const reviews = { decideBatch: vi.fn(), reviewPeriod: vi.fn(() => EMPTY) };
  const dialog = { open: vi.fn() };
  const organizations = {
    ofType: (type: OrganizationType) =>
      of(
        type === 'FACULTY'
          ? [unit(9, 'FACULTY', null), unit(10, 'FACULTY', null)]
          : [unit(64, 'DEPARTMENT', 9), unit(70, 'DEPARTMENT', 10)],
      ),
  };

  async function create(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [
        ReviewListComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideRouter([]),
        provideStore(),
        provideState(reviewsFeature),
        { provide: OrganizationsService, useValue: organizations },
        { provide: BreakpointObserver, useValue: { observe: () => wide } },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        { provide: AuthService, useValue: { permissions } },
        { provide: ReviewsService, useValue: reviews },
        { provide: MatDialog, useValue: dialog },
      ],
    }).compileComponents();
    store = TestBed.inject(Store);
    vi.spyOn(store, 'dispatch');
    fixture = TestBed.createComponent(ReviewListComponent);
    fixture.detectChanges();
  }

  function load(items: ReviewItem[]): void {
    store.dispatch(
      ReviewsActions.reviewsLoaded({
        page: { content: items, totalElements: items.length, totalPages: 1, size: 20, number: 0 },
      }),
    );
    fixture.detectChanges();
  }

  beforeEach(() => {
    wide.next({ matches: true, breakpoints: {} });
    reviews.decideBatch.mockReset();
    dialog.open.mockReset();
  });

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function tick(testId: string, index = 0): void {
    const boxes = element().querySelectorAll<HTMLElement>(
      `[data-testid="${testId}"] input[type="checkbox"]`,
    );
    boxes[index].click();
    fixture.detectChanges();
  }

  function text(testId: string): string {
    return element().querySelector(`[data-testid="${testId}"]`)?.textContent?.trim() ?? '';
  }

  function confirmWith(comment?: string): void {
    dialog.open.mockReturnValue({ afterClosed: () => of(comment ? { comment } : {}) });
  }

  it('ac1_10_shows_a_row_per_request_with_deadline_overdue_chip_and_reviewer', async () => {
    await create();
    load([
      reviewItem(),
      reviewItem({
        awardId: 6,
        requestId: 9,
        overdue: true,
        reviewer: { id: 31, name: 'Олена Петрук', email: 'secretary2.fmi@chnu.edu.ua' },
      }),
    ]);
    const element: HTMLElement = fixture.nativeElement;
    const rows = element.querySelectorAll('[data-testid="review-item"]');

    expect(store.dispatch).toHaveBeenCalledWith(ReviewsActions.opened());
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Анастасія Коваль');
    expect(rows[0].textContent).toContain('Кафедра алгебри');
    expect(rows[0].textContent).toContain('Національний');
    expect(rows[0].textContent).toContain('06.10.2026');
    expect(rows[0].querySelector('[data-testid="review-overdue"]')).toBeNull();
    expect(rows[1].querySelector('[data-testid="review-overdue"]')?.textContent).toContain(
      'Прострочено',
    );
    expect(rows[1].querySelector('[data-testid="review-reviewer"]')?.textContent).toContain(
      'Олена Петрук',
    );
  });

  it('ac2_4_a_noticed_request_shows_the_notice_next_to_the_overdue_chip', async () => {
    await create();
    load([
      reviewItem(),
      reviewItem({
        awardId: 6,
        requestId: 9,
        overdue: true,
        overdueNoticedAt: '2026-10-07T08:05:00Z',
      }),
    ]);
    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll(
      '[data-testid="review-item"]',
    );

    expect(rows[0].querySelector('[data-testid="review-noticed"]')).toBeNull();
    expect(rows[1].querySelector('[data-testid="review-noticed"]')?.textContent).toContain(
      'Керівника повідомлено 07.10.2026',
    );
  });

  it('ac2_4_the_noticed_filter_toggles_the_query', async () => {
    await create();
    const element: HTMLElement = fixture.nativeElement;

    element.querySelector<HTMLInputElement>('[data-testid="review-filter-noticed"] input')?.click();

    expect(store.dispatch).toHaveBeenCalledWith(
      ReviewsActions.filtersChanged({ filters: { ...NO_REVIEW_FILTERS, noticed: true } }),
    );
    expect(element.querySelector('[data-testid="review-filter-lower-overdue"]')).toBeNull();
  });

  it('ac2_4_a_dean_lists_the_noticed_requests_of_the_faculty_secretaries', async () => {
    const own = permissions();
    permissions.set(readPermissions(token({ role_scopes: ['DEAN:9'] })));
    await create();

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLElement>('[data-testid="review-filter-lower-overdue"]')
      ?.click();

    expect(store.dispatch).toHaveBeenCalledWith(
      ReviewsActions.filtersChanged({
        filters: { ...NO_REVIEW_FILTERS, level: 'FACULTY_SECRETARY', noticed: true },
      }),
    );
    permissions.set(own);
  });

  it('ac1_10_opens_the_award_on_a_row_click', async () => {
    await create();
    load([reviewItem()]);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLElement>('[data-testid="review-item"]')
      ?.click();

    expect(navigate).toHaveBeenCalledWith(['/awards', 5]);
  });

  it('ac1_10_tabs_set_the_assignment_filter', async () => {
    await create();
    const element: HTMLElement = fixture.nativeElement;

    element.querySelector<HTMLElement>('[data-testid="review-tab-me"]')?.click();

    expect(store.dispatch).toHaveBeenCalledWith(
      ReviewsActions.filtersChanged({ filters: { ...NO_REVIEW_FILTERS, assigned: 'me' } }),
    );
    expect(element.querySelector('[data-testid="review-tab-all"]')?.classList).toContain(
      'mdc-tab--active',
    );
  });

  it('ac1_2_offers_the_units_and_levels_inside_the_callers_scopes', async () => {
    await create();
    const component = fixture.componentInstance as unknown as {
      units: () => OrganizationSummary[];
      levels: string[];
    };

    expect(component.units().map((organization) => organization.id)).toEqual([64, 9]);
    expect(component.levels).toEqual(['FACULTY_SECRETARY']);
  });

  it('ac1_13_shows_cards_instead_of_the_table_on_a_narrow_screen', async () => {
    wide.next({ matches: false, breakpoints: {} });
    await create();
    load([reviewItem()]);
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('[data-testid="review-table"]')).toBeNull();
    expect(
      element.querySelectorAll('[data-testid="review-cards"] [data-testid="review-item"]').length,
    ).toBe(1);
  });

  it('ac1_3_shows_the_empty_queue', async () => {
    await create();
    load([]);

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="reviews-empty"]'),
    ).not.toBeNull();
  });

  it('ac4_6_the_header_selects_the_page_and_the_action_bar_counts_it', async () => {
    await create();
    load([reviewItem(), reviewItem({ awardId: 6, requestId: 9 })]);

    expect(element().querySelector('[data-testid="batch-actions"]')).toBeNull();
    tick('batch-select-all');

    expect(text('batch-selected')).toBe('Вибрано: 2');
    expect(text('batch-approve')).toBe('Затвердити (2)');
    expect(text('batch-escalate')).toBe('Передати декану');
    tick('batch-select-all');
    expect(element().querySelector('[data-testid="batch-actions"]')).toBeNull();
  });

  it('ac4_6_a_row_checkbox_does_not_open_the_award', async () => {
    await create();
    load([reviewItem()]);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    tick('batch-select');

    expect(navigate).not.toHaveBeenCalled();
    expect(text('batch-selected')).toBe('Вибрано: 1');
  });

  it('ac4_6_mixed_levels_escalate_to_the_next_level_of_each', async () => {
    await create();
    load([reviewItem(), reviewItem({ awardId: 6, requestId: 9, level: 'DEAN' })]);

    tick('batch-select-all');

    expect(text('batch-escalate')).toBe('Передати на вищий рівень');
  });

  it('ac4_1_ac4_7_a_batch_removes_decided_rows_and_keeps_the_failed_selected', async () => {
    await create();
    load([
      reviewItem(),
      reviewItem({ awardId: 6, requestId: 9, requestVersion: 1 }),
      reviewItem({ awardId: 7, requestId: 10, titleUk: 'Подяка ректора' }),
    ]);
    const results: BatchItemResult[] = [
      { awardId: 5, outcome: 'DONE', status: 'APPROVED', level: 'FACULTY_SECRETARY' },
      { awardId: 6, outcome: 'DONE', status: 'APPROVED', level: 'FACULTY_SECRETARY' },
      { awardId: 7, outcome: 'FAILED', code: 'request-claimed', detail: 'Claimed' },
    ];
    reviews.decideBatch.mockReturnValue(of(results));
    confirmWith();
    tick('batch-select-all');

    element().querySelector<HTMLElement>('[data-testid="batch-approve"]')?.click();
    fixture.detectChanges();

    expect(dialog.open.mock.calls[0][1].data).toEqual({
      decision: 'APPROVE',
      target: null,
      documents: 0,
      count: 3,
    });
    expect(reviews.decideBatch).toHaveBeenCalledWith({
      decision: 'APPROVE',
      items: [
        { awardId: 5, requestVersion: 3 },
        { awardId: 6, requestVersion: 1 },
        { awardId: 7, requestVersion: 3 },
      ],
    });
    expect(text('batch-done')).toBe('Опрацьовано: 2 з 3');
    expect(text('batch-failed')).toContain('Подяка ректора');
    expect(text('batch-reason')).toBe('Взято в роботу іншим рецензентом');
    expect(element().querySelector('[data-testid="batch-failed"] a')?.getAttribute('href')).toBe(
      '/awards/7',
    );
    expect(element().querySelectorAll('[data-testid="review-item"]').length).toBe(1);
    expect(text('batch-selected')).toBe('Вибрано: 1');
    expect(store.dispatch).toHaveBeenCalledWith(
      ReviewsActions.pageChanged({ pageIndex: 0, pageSize: 20 }),
    );
  });

  it('ac4_7_a_batch_without_failures_does_not_reload_the_queue', async () => {
    await create();
    load([reviewItem()]);
    reviews.decideBatch.mockReturnValue(of([{ awardId: 5, outcome: 'DONE' }]));
    confirmWith();
    tick('batch-select');

    element().querySelector<HTMLElement>('[data-testid="batch-approve"]')?.click();

    expect(store.dispatch).not.toHaveBeenCalledWith(
      ReviewsActions.pageChanged({ pageIndex: 0, pageSize: 20 }),
    );
  });

  it('ac4_6_a_secretary_level_escalation_names_the_dean_and_sends_the_comment', async () => {
    await create();
    load([reviewItem()]);
    reviews.decideBatch.mockReturnValue(of([{ awardId: 5, outcome: 'DONE' }]));
    confirmWith('Потребує рішення декана');
    tick('batch-select');

    element().querySelector<HTMLElement>('[data-testid="batch-escalate"]')?.click();

    expect(dialog.open.mock.calls[0][1].data.target).toBe('DEAN');
    expect(reviews.decideBatch).toHaveBeenCalledWith(
      expect.objectContaining({ decision: 'ESCALATE', comment: 'Потребує рішення декана' }),
    );
  });

  it('ac4_6_a_cancelled_dialog_sends_nothing', async () => {
    await create();
    load([reviewItem()]);
    dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });
    tick('batch-select');

    element().querySelector<HTMLElement>('[data-testid="batch-return"]')?.click();

    expect(reviews.decideBatch).not.toHaveBeenCalled();
  });

  it('ac4_7_an_unreachable_server_reloads_the_queue', async () => {
    await create();
    load([reviewItem()]);
    reviews.decideBatch.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    confirmWith();
    tick('batch-select');

    element().querySelector<HTMLElement>('[data-testid="batch-reject"]')?.click();
    fixture.detectChanges();

    expect(element().querySelector('[data-testid="batch-error"]')).not.toBeNull();
    expect(store.dispatch).toHaveBeenCalledWith(
      ReviewsActions.pageChanged({ pageIndex: 0, pageSize: 20 }),
    );
  });

  it('ac4_8_cards_offer_the_same_selection_on_a_narrow_screen', async () => {
    wide.next({ matches: false, breakpoints: {} });
    await create();
    load([reviewItem(), reviewItem({ awardId: 6, requestId: 9 })]);

    tick('batch-select', 1);
    expect(text('batch-selected')).toBe('Вибрано: 1');
    tick('batch-select-all');
    expect(text('batch-selected')).toBe('Вибрано: 2');
  });
});
