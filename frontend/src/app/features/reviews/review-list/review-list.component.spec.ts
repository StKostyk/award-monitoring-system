import { BreakpointObserver } from '@angular/cdk/layout';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Store, provideState, provideStore } from '@ngrx/store';
import { BehaviorSubject, of } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { readPermissions } from '../../../core/auth/permissions';
import { OrganizationType } from '../../../core/auth/user-profile';
import { LanguageService } from '../../../core/i18n/language.service';
import {
  OrganizationSummary,
  OrganizationsService,
} from '../../../core/organizations/organizations.service';
import { ReviewItem, NO_REVIEW_FILTERS } from '../reviews.service';
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
      empty: 'Немає нагород на розгляді',
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

  beforeEach(() => wide.next({ matches: true, breakpoints: {} }));

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
});
