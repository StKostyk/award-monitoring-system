import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { LanguageService } from '../../../core/i18n/language.service';
import {
  Award,
  AwardSnapshot,
  AwardVersion,
  AwardsService,
  CategoryNode,
  Page,
} from '../awards.service';
import { AwardHistoryComponent } from './award-history.component';
import { AwardVersionDialogComponent } from './award-version-dialog.component';

const award = {
  id: 5,
  organization: {
    id: 64,
    name: 'Algebra',
    nameUk: 'Кафедра алгебри',
    code: 'DAI',
    type: 'DEPARTMENT',
  },
} as Award;

const draft: AwardSnapshot = {
  title: 'Letter',
  titleUk: null,
  description: null,
  descriptionUk: null,
  awardingOrganization: null,
  awardDate: null,
  categoryId: null,
  status: 'DRAFT',
  impactScore: null,
  verificationBadge: false,
  externalUrl: null,
  organizationId: 64,
};

const ministry: CategoryNode = {
  id: 13,
  name: 'Ministry Recognition',
  nameUk: 'Відзнака міністерства',
  level: 'NATIONAL',
  description: null,
  children: [],
};

function version(number: number, overrides: Partial<AwardVersion> = {}): AwardVersion {
  return {
    number,
    action: 'CREATED',
    actor: { id: 21, name: 'Анастасія Коваль', email: 'employee.fmi@chnu.edu.ua' },
    createdAt: '2026-09-30T08:05:00Z',
    snapshot: draft,
    changes: [],
    ...overrides,
  };
}

function page(content: AwardVersion[], totalPages = 1): Page<AwardVersion> {
  return { content, totalElements: content.length, totalPages, size: 20, number: 0 };
}

const translations = {
  uk: {
    awards: {
      fields: { title: 'Назва англійською', category: 'Категорія', organization: 'Підрозділ' },
      history: {
        actions: { CREATED: 'Створено', UPDATED: 'Змінено', BASELINE: 'Початковий стан' },
        unknownActor: 'Невідомий користувач',
        system: 'Система',
        failed: 'Не вдалося',
      },
    },
  },
};

describe('AwardHistoryComponent', () => {
  const service = { versions: vi.fn(), categories: vi.fn(), organizations: vi.fn() };
  const dialog = { open: vi.fn() };

  async function render(): Promise<ComponentFixture<AwardHistoryComponent>> {
    await TestBed.configureTestingModule({
      imports: [
        AwardHistoryComponent,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        { provide: AwardsService, useValue: service },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        { provide: MatDialog, useValue: dialog },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(AwardHistoryComponent);
    fixture.componentRef.setInput('award', award);
    fixture.detectChanges();
    return fixture;
  }

  function texts(fixture: ComponentFixture<AwardHistoryComponent>, testId: string): string[] {
    const nodes = (fixture.nativeElement as HTMLElement).querySelectorAll(
      `[data-testid="${testId}"]`,
    );
    return Array.from(nodes, (node) => node.textContent?.replace(/\s+/g, ' ').trim() ?? '');
  }

  beforeEach(() => {
    service.versions.mockReset();
    service.categories.mockReset().mockReturnValue(of([ministry]));
    service.organizations.mockReset();
    dialog.open.mockReset();
  });

  it('ac2_1_lists_versions_with_actor_kyiv_time_and_named_changes', async () => {
    service.versions.mockReturnValue(
      of(
        page([
          version(2, {
            action: 'UPDATED',
            createdAt: '2026-09-30T09:15:00Z',
            changes: [
              { field: 'title', from: 'Letter', to: 'Diploma' },
              { field: 'categoryId', from: null, to: 13 },
            ],
          }),
          version(1),
        ]),
      ),
    );
    const fixture = await render();

    expect(texts(fixture, 'award-history-action')).toEqual(['Змінено', 'Створено']);
    expect(texts(fixture, 'award-history-meta')[0]).toBe('Анастасія Коваль · 30.09.2026, 12:15');
    expect(texts(fixture, 'award-history-change')).toEqual([
      'Назва англійською: Letter → Diploma',
      'Категорія: — → Відзнака міністерства',
    ]);
  });

  it('ac2_1_names_a_missing_actor_and_the_system_baseline', async () => {
    service.versions.mockReturnValue(
      of(page([version(2, { actor: null }), version(1, { action: 'BASELINE', actor: null })])),
    );
    const fixture = await render();

    const meta = texts(fixture, 'award-history-meta');
    expect(meta[0]).toContain('Невідомий користувач');
    expect(meta[1]).toContain('Система');
  });

  it('ac2_2_opens_the_dialog_with_the_chosen_version', async () => {
    const first = version(1);
    service.versions.mockReturnValue(of(page([first])));
    const fixture = await render();

    (
      fixture.nativeElement.querySelector('[data-testid="award-history-view"]') as HTMLButtonElement
    ).click();

    expect(dialog.open).toHaveBeenCalledWith(
      AwardVersionDialogComponent,
      expect.objectContaining({ data: expect.objectContaining({ version: first }) }),
    );
  });

  it('ac2_3_loads_the_next_page_and_hides_the_button_after_the_last', async () => {
    service.versions
      .mockReturnValueOnce(of(page([version(22), version(21)], 2)))
      .mockReturnValueOnce(of(page([version(21), version(1)], 2)));
    const fixture = await render();

    (
      fixture.nativeElement.querySelector('[data-testid="award-history-more"]') as HTMLButtonElement
    ).click();
    fixture.detectChanges();

    expect(service.versions).toHaveBeenLastCalledWith(5, 1, 20);
    expect(fixture.componentInstance.versions().map((shown) => shown.number)).toEqual([22, 21, 1]);
    expect(fixture.nativeElement.querySelector('[data-testid="award-history-more"]')).toBeNull();
  });

  it('ac2_3_shows_a_failed_load_with_a_retry', async () => {
    service.versions
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })))
      .mockReturnValueOnce(of(page([version(1)])));
    const fixture = await render();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('[data-testid="award-history-error"]')?.textContent).toContain(
      'Не вдалося',
    );
    (element.querySelector('[data-testid="award-history-retry"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(element.querySelector('[data-testid="award-history-error"]')).toBeNull();
    expect(texts(fixture, 'award-history-action')).toEqual(['Створено']);
  });

  it('ac2_1_looks_up_an_organisation_other_than_the_awards_by_name', async () => {
    service.versions.mockReturnValue(
      of(
        page([
          version(2, {
            action: 'UPDATED',
            changes: [{ field: 'organizationId', from: 64, to: 70 }],
          }),
        ]),
      ),
    );
    service.organizations.mockReturnValue(
      of(new Map([[70, { id: 70, name: 'Geometry', nameUk: 'Кафедра геометрії' }]])),
    );
    const fixture = await render();
    fixture.detectChanges();

    expect(texts(fixture, 'award-history-change')).toEqual([
      'Підрозділ: Кафедра алгебри → Кафедра геометрії',
    ]);
  });

  it('ac2_1_needs_no_organisation_lookup_when_the_award_names_them_all', async () => {
    service.versions.mockReturnValue(of(page([version(1)])));
    await render();

    expect(service.organizations).not.toHaveBeenCalled();
  });
});
