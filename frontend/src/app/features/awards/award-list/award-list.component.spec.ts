import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Store, provideState, provideStore } from '@ngrx/store';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { readPermissions } from '../../../core/auth/permissions';
import { LanguageService } from '../../../core/i18n/language.service';
import { Award, AwardsService } from '../awards.service';
import { AwardsActions } from '../store/awards.actions';
import { awardsFeature } from '../store/awards.feature';
import { AwardListComponent } from './award-list.component';

function award(overrides: Partial<Award> = {}): Award {
  return {
    id: 5,
    title: null,
    titleUk: 'Грамота Міністерства освіти і науки',
    description: null,
    descriptionUk: null,
    category: { id: 13, name: 'Ministry Recognition', nameUk: 'Відзнака міністерства', level: 'NATIONAL' },
    awardingOrganization: 'МОН України',
    awardDate: '2025-05-01',
    externalUrl: null,
    status: 'DRAFT',
    impactScore: null,
    owner: { id: 21, name: 'Анастасія Коваль', email: 'employee.fmi@chnu.edu.ua' },
    organization: { id: 64, name: 'Algebra', nameUk: 'Кафедра алгебри', code: 'DAI', type: 'DEPARTMENT' },
    request: null,
    warnings: [],
    createdAt: '2026-09-28T08:00:00Z',
    updatedAt: '2026-09-28T08:00:00Z',
    version: 1,
    ...overrides,
  };
}

function token(claims: Record<string, unknown>): string {
  return `header.${btoa(JSON.stringify(claims))}.signature`;
}

const translations = {
  uk: {
    awards: {
      title: 'Мої нагороди',
      add: 'Додати нагороду',
      empty: 'Нагород ще немає.',
      status: { DRAFT: 'Чернетка', PENDING: 'На розгляді' },
    },
  },
};

describe('AwardListComponent', () => {
  let fixture: ComponentFixture<AwardListComponent>;
  let store: Store;
  const permissions = signal(readPermissions(token({ permissions: ['award:read:own', 'award:create'] })));

  async function create(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [
        AwardListComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideRouter([]),
        provideStore(),
        provideState(awardsFeature),
        { provide: AwardsService, useValue: { categories: () => of([]) } },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        { provide: AuthService, useValue: { permissions } },
      ],
    }).compileComponents();
    store = TestBed.inject(Store);
    vi.spyOn(store, 'dispatch');
    fixture = TestBed.createComponent(AwardListComponent);
    fixture.detectChanges();
  }

  it('ac1_9_lists_own_awards_with_their_status_chip_and_links_drafts_to_the_form', async () => {
    await create();
    store.dispatch(
      AwardsActions.awardsLoaded({
        page: {
          content: [award(), award({ id: 6, status: 'PENDING', titleUk: 'Подяка' })],
          totalElements: 2,
          totalPages: 1,
          size: 100,
          number: 0,
        },
      }),
    );
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    const items = element.querySelectorAll('[data-testid="award-item"]');

    expect(items.length).toBe(2);
    expect(items[0].textContent).toContain('Відзнака міністерства');
    expect(items[0].querySelector('[data-testid="award-status"]')?.textContent).toContain('Чернетка');
    expect(items[0].querySelector('a')?.getAttribute('href')).toBe('/awards/5/edit');
    expect(items[1].querySelector('a')?.getAttribute('href')).toBe('/awards/6');
    expect(element.querySelector('[data-testid="award-add"]')).not.toBeNull();
  });

  it('ac1_9_shows_the_empty_list_and_asks_for_the_first_award', async () => {
    await create();
    store.dispatch(
      AwardsActions.awardsLoaded({
        page: { content: [], totalElements: 0, totalPages: 0, size: 100, number: 0 },
      }),
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="awards-empty"]')?.textContent).toContain(
      'Нагород ще немає.',
    );
  });

  it('ac1_7_changing_a_filter_reloads_the_list', async () => {
    await create();

    fixture.componentInstance.filter({ status: 'PENDING' });

    expect(store.dispatch).toHaveBeenCalledWith(
      AwardsActions.filtersChanged({
        filters: { status: 'PENDING', category: null, dateFrom: null, dateTo: null },
      }),
    );
  });

  it('ac1_2_hides_the_add_button_without_award_create', async () => {
    permissions.set(readPermissions(token({ permissions: ['award:read:own'] })));
    await create();

    expect(fixture.nativeElement.querySelector('[data-testid="award-add"]')).toBeNull();
  });
});
