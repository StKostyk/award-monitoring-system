import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { provideIsoDateAdapter } from '../../../core/i18n/iso-date-adapter';
import { LanguageService } from '../../../core/i18n/language.service';
import { ReviewItem, ReviewsService } from '../../reviews/reviews.service';
import { DocumentsService } from '../award-documents/documents.service';
import { Award, AwardsService, CategoryNode } from '../awards.service';
import { AwardCorrectionComponent } from './award-correction.component';
import { CorrectionDialogData } from './correction-dialog.component';

const SELF = { id: 31, name: 'Ірина Секретар', email: 'secretary.fmi@chnu.edu.ua' };
const PEER = { id: 32, name: 'Олена Петрук', email: 'secretary2.fmi@chnu.edu.ua' };

const tree: CategoryNode[] = [
  {
    id: 21,
    name: 'University Excellence Award',
    nameUk: 'Нагорода університетської досконалості',
    level: 'UNIVERSITY',
    description: null,
    children: [],
  },
  {
    id: 13,
    name: 'Ministry Recognition',
    nameUk: 'Відзнака міністерства',
    level: 'NATIONAL',
    description: null,
    children: [],
  },
];

function award(overrides: Partial<Award> = {}): Award {
  return {
    id: 5,
    title: null,
    titleUk: 'Грамота',
    description: null,
    descriptionUk: null,
    category: tree[0],
    awardingOrganization: 'МОН України',
    awardDate: '2025-05-01',
    externalUrl: 'https://mon.gov.ua',
    status: 'PENDING',
    impactScore: 50,
    owner: { id: 21, name: 'Анастасія Коваль', email: 'employee.fmi@chnu.edu.ua' },
    recipient: { type: 'PERSON', organization: null },
    organization: {
      id: 64,
      name: 'Algebra',
      nameUk: 'Кафедра алгебри',
      code: 'DAI',
      type: 'DEPARTMENT',
    },
    request: null,
    warnings: [],
    createdAt: '2026-09-28T08:00:00Z',
    updatedAt: '2026-09-28T08:00:00Z',
    version: 4,
    ...overrides,
  };
}

function item(overrides: Partial<ReviewItem> = {}): ReviewItem {
  return {
    awardId: 5,
    requestId: 8,
    requestVersion: 3,
    title: null,
    titleUk: 'Грамота',
    recipient: { type: 'PERSON', organization: null },
    owner: { id: 21, name: 'Анастасія Коваль', email: 'employee.fmi@chnu.edu.ua' },
    organization: null,
    category: null,
    level: 'FACULTY_SECRETARY',
    status: 'SUBMITTED',
    reviewer: null,
    submittedAt: '2026-10-01T08:00:00Z',
    deadline: '2026-10-06T20:59:59Z',
    overdue: false,
    overdueNoticedAt: null,
    documentCount: 0,
    delegatedFrom: null,
    ...overrides,
  };
}

function problem(
  type: string,
  status: number,
  body: Record<string, unknown> = {},
): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: { type: `urn:awards:problem:${type}`, ...body } });
}

describe('AwardCorrectionComponent', () => {
  let fixture: ComponentFixture<AwardCorrectionComponent>;
  let component: AwardCorrectionComponent;
  let router: Router;
  const service = { categories: vi.fn(() => of(tree)), get: vi.fn(), correct: vi.fn() };
  const reviews = { item: vi.fn() };
  const dialog = { open: vi.fn() };
  const documents = { list: vi.fn(() => of([])), download: vi.fn() };

  async function open(id = '5'): Promise<HTMLElement> {
    await TestBed.configureTestingModule({
      imports: [
        AwardCorrectionComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: { uk: {} },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideIsoDateAdapter(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id }) } },
        },
        { provide: AwardsService, useValue: service },
        { provide: ReviewsService, useValue: reviews },
        { provide: MatDialog, useValue: dialog },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        { provide: AuthService, useValue: { userId: () => String(SELF.id) } },
        { provide: DocumentsService, useValue: documents },
      ],
    }).compileComponents();
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(AwardCorrectionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function type(field: string, value: string | number | null): void {
    const control = component.form.get(field);
    control?.setValue(value as never);
    control?.markAsDirty();
    fixture.detectChanges();
  }

  function saveButton(element: HTMLElement): HTMLButtonElement {
    return element.querySelector<HTMLButtonElement>('[data-testid="correction-save"]')!;
  }

  function confirmWith(answer: boolean): void {
    dialog.open.mockReturnValue({ afterClosed: () => of(answer) });
  }

  beforeEach(() => {
    service.get.mockReset().mockReturnValue(of(award()));
    service.correct.mockReset();
    reviews.item.mockReset().mockReturnValue(of(item()));
    dialog.open.mockReset();
  });

  it('ac3_2_opens_with_the_current_values_and_the_recipient_read_only', async () => {
    const element = await open();

    expect(component.form.controls.titleUk.value).toBe('Грамота');
    expect(component.form.controls.categoryId.value).toBe(21);
    expect(component.form.controls.awardDate.value).toBe('2025-05-01');
    expect(element.querySelector('[data-testid="correction-recipient"]')?.textContent?.trim()).toBe(
      'Анастасія Коваль',
    );
    expect(element.querySelector('[data-testid="award-recipient-unit"]')).toBeNull();
    expect(element.querySelector('app-award-documents')).not.toBeNull();
  });

  it('ac3_2_enables_save_only_when_a_field_differs', async () => {
    const element = await open();
    expect(saveButton(element).disabled).toBe(true);

    type('reason', 'Дата з наказу');
    expect(saveButton(element).disabled).toBe(true);

    type('awardDate', '2025-06-01');
    expect(saveButton(element).disabled).toBe(false);

    type('awardDate', '2025-05-01');
    expect(saveButton(element).disabled).toBe(true);
    expect(component.changed()).toEqual([]);
  });

  it('ac3_2_requires_a_reason_before_asking', async () => {
    await open();
    type('titleUk', 'Подяка');

    component.save();

    expect(dialog.open).not.toHaveBeenCalled();
    expect(component.errorKey('reason')).toBe('awards.errors.required');
  });

  it('ac3_2_ac3_3_confirms_the_changes_and_sends_only_the_changed_fields', async () => {
    await open();
    confirmWith(true);
    service.correct.mockReturnValue(of({ award: award(), requestVersion: 4, changedFields: [] }));
    type('categoryId', 13);
    type('externalUrl', '');
    type('reason', '  Категорія з наказу ');

    component.save();

    const data = dialog.open.mock.calls[0][1].data as CorrectionDialogData;
    expect(data.reason).toBe('Категорія з наказу');
    expect(data.changes).toEqual([
      {
        label: 'awards.fields.category',
        from: { text: 'Нагорода університетської досконалості' },
        to: { text: 'Відзнака міністерства' },
      },
      {
        label: 'awards.fields.externalUrl',
        from: { text: 'https://mon.gov.ua' },
        to: { text: '—' },
      },
    ]);
    expect(service.correct).toHaveBeenCalledWith(5, {
      categoryId: 13,
      externalUrl: null,
      version: 4,
      requestVersion: 3,
      reason: 'Категорія з наказу',
    });
    expect(router.navigate).toHaveBeenCalledWith(['/awards', 5], {
      state: { notice: 'awards.correction.done' },
    });
    expect(component.confirmLeave()).toBe(true);
  });

  it('ac3_2_sends_nothing_when_the_confirmation_is_cancelled', async () => {
    await open();
    confirmWith(false);
    type('titleUk', 'Подяка');
    type('reason', 'Назва');

    component.save();

    expect(service.correct).not.toHaveBeenCalled();
  });

  it('ac3_1_refuses_a_request_a_colleague_holds', async () => {
    reviews.item.mockReturnValue(of(item({ reviewer: PEER })));

    const element = await open();

    expect(element.querySelector('[data-testid="correction-held"]')).not.toBeNull();
    expect(element.querySelector('form')).toBeNull();
  });

  it('ac3_1_opens_for_the_holder', async () => {
    reviews.item.mockReturnValue(of(item({ reviewer: SELF })));

    const element = await open();

    expect(element.querySelector('form')).not.toBeNull();
  });

  it('ac3_7_shows_an_award_that_is_not_pending_or_not_reviewable_as_unavailable', async () => {
    service.get.mockReturnValue(of(award({ status: 'APPROVED' })));
    let element = await open();
    expect(element.querySelector('[data-testid="correction-unavailable"]')).not.toBeNull();

    TestBed.resetTestingModule();
    service.get.mockReturnValue(of(award()));
    reviews.item.mockReturnValue(throwError(() => problem('not-found', 404)));
    element = await open();
    expect(element.querySelector('[data-testid="correction-unavailable"]')).not.toBeNull();

    TestBed.resetTestingModule();
    element = await open('abc');
    expect(element.querySelector('[data-testid="correction-unavailable"]')).not.toBeNull();
  });

  it('ac3_7_names_the_colleague_who_claimed_it_meanwhile', async () => {
    const element = await open();
    confirmWith(true);
    service.correct.mockReturnValue(
      throwError(() => problem('request-claimed', 409, { reviewer: PEER })),
    );
    type('titleUk', 'Подяка');
    type('reason', 'Назва');

    component.save();
    fixture.detectChanges();

    expect(component.heldBy()).toEqual(PEER);
    expect(element.querySelector('[data-testid="correction-held"]')).not.toBeNull();
  });

  it('ac3_7_marks_invalid_fields_and_offers_a_reload_when_stale', async () => {
    await open();
    confirmWith(true);
    service.correct
      .mockReturnValueOnce(
        throwError(() =>
          problem('validation-failed', 422, {
            errors: [{ field: 'externalUrl', code: 'invalid', message: 'x' }],
          }),
        ),
      )
      .mockReturnValueOnce(throwError(() => problem('request-stale', 409)))
      .mockReturnValueOnce(throwError(() => problem('no-change', 422)));
    type('titleUk', 'Подяка');
    type('reason', 'Назва');

    component.save();
    expect(component.errorKey('externalUrl')).toBe('awards.errors.invalid');
    expect(component.problem()).toBe('awards.correction.problems.validation-failed');

    component.save();
    expect(component.stale()).toBe(true);
    component.reload();
    expect(service.get).toHaveBeenCalledTimes(2);
    expect(component.stale()).toBe(false);

    type('titleUk', 'Подяка');
    type('reason', 'Назва');
    component.save();
    expect(component.problem()).toBe('awards.correction.problems.no-change');
  });

  it('ac3_7_asks_before_leaving_with_unsaved_changes', async () => {
    await open();
    expect(component.confirmLeave()).toBe(true);
    confirmWith(true);

    type('titleUk', 'Подяка');

    expect(component.confirmLeave()).not.toBe(true);
  });
});
