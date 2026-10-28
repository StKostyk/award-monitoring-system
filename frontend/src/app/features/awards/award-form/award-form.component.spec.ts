import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import {
  ComponentFixture,
  DeferBlockBehavior,
  DeferBlockState,
  TestBed,
} from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Observable, Subject, of, tap, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { ConnectivityService } from '../../../core/connectivity/connectivity.service';
import { provideIsoDateAdapter } from '../../../core/i18n/iso-date-adapter';
import { LanguageService } from '../../../core/i18n/language.service';
import { FormCopiesService } from '../../../core/storage/form-copies.service';
import { DocumentsService } from '../award-documents/documents.service';
import {
  Award,
  AwardCreation,
  AwardsService,
  CategoryNode,
  CategorySuggestion,
  UnitRef,
} from '../awards.service';
import { AwardFormComponent } from './award-form.component';

const tree: CategoryNode[] = [
  {
    id: 10,
    name: 'National Awards',
    nameUk: 'Національні нагороди',
    level: 'NATIONAL',
    description: null,
    children: [
      {
        id: 13,
        name: 'Ministry Recognition',
        nameUk: 'Відзнака міністерства',
        level: 'NATIONAL',
        description: null,
        children: [],
      },
    ],
  },
];

const units: UnitRef[] = [
  { id: 9, name: 'Faculty of Mathematics', nameUk: 'Факультет математики', type: 'FACULTY' },
  { id: 64, name: 'Algebra', nameUk: 'Кафедра алгебри', type: 'DEPARTMENT' },
];

function award(overrides: Partial<Award> = {}): Award {
  return {
    id: 5,
    title: null,
    titleUk: 'Грамота',
    description: null,
    descriptionUk: null,
    category: null,
    awardingOrganization: null,
    awardDate: null,
    externalUrl: null,
    status: 'DRAFT',
    impactScore: null,
    owner: { id: 21, name: 'Анастасія Коваль', email: 'employee.fmi@chnu.edu.ua' },
    recipient: { type: 'PERSON', organization: null },
    organization: {
      id: 64,
      name: 'Algebra',
      nameUk: 'Кафедра алгебри',
      code: 'DAI',
      type: 'DEPARTMENT',
    },
    visibility: 'PRIVATE',
    request: null,
    warnings: [],
    createdAt: '2026-09-28T08:00:00Z',
    updatedAt: '2026-09-28T08:00:00Z',
    version: 1,
    ...overrides,
  };
}

const NEW_KEY = 'award-new-key';
const STORED_KEY = '6f1d2c3b-4a5e-4f60-8172-93a4b5c6d7e8';

function problem(type: string, status: number, errors: unknown[] = []): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: { type: `urn:awards:problem:${type}`, errors } });
}

function created(saved: Award, repeated = false): Observable<AwardCreation> {
  return of({ award: saved, repeated });
}

describe('AwardFormComponent', () => {
  let fixture: ComponentFixture<AwardFormComponent>;
  let component: AwardFormComponent;
  let router: Router;
  let location: Location;
  let copies: FormCopiesService;
  const service = {
    categories: vi.fn(() => of(tree)),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    submit: vi.fn(),
    remove: vi.fn(),
    suggestions: vi.fn((): Observable<CategorySuggestion[]> => of([])),
    recipientUnits: vi.fn((): Observable<UnitRef[]> => of([])),
  };
  const dialog = { open: vi.fn() };
  const documents = {
    list: vi.fn(() => of([])),
    upload: vi.fn(),
    download: vi.fn(),
    remove: vi.fn(),
  };
  const auth = { userId: signal<string | null>('21'), isAuthenticated: signal(true) };

  async function open(id: string | null): Promise<void> {
    await TestBed.configureTestingModule({
      deferBlockBehavior: DeferBlockBehavior.Manual,
      imports: [
        AwardFormComponent,
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
          useValue: { snapshot: { paramMap: convertToParamMap(id ? { id } : {}) } },
        },
        { provide: AwardsService, useValue: service },
        { provide: MatDialog, useValue: dialog },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        { provide: AuthService, useValue: auth },
        { provide: DocumentsService, useValue: documents },
      ],
    }).compileComponents();
    router = TestBed.inject(Router);
    location = TestBed.inject(Location);
    copies = TestBed.inject(FormCopiesService);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    vi.spyOn(location, 'replaceState');
    fixture = TestBed.createComponent(AwardFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    for (const block of await fixture.getDeferBlocks()) {
      await block.render(DeferBlockState.Complete);
    }
    component.documentCount.set(1);
  }

  function type(field: string, value: string | number): void {
    const control = component.form.get(field);
    control?.setValue(value as never);
    control?.markAsDirty();
  }

  function fillComplete(): void {
    type('titleUk', 'Грамота');
    type('categoryId', 13);
    type('awardingOrganization', 'МОН України');
    type('awardDate', '2025-05-01');
  }

  function duplicateOf(matches: unknown[]): HttpErrorResponse {
    return new HttpErrorResponse({
      status: 409,
      error: { type: 'urn:awards:problem:award-possible-duplicate', matches },
    });
  }

  beforeEach(() => {
    localStorage.clear();
    Object.values(service).forEach((mock) => mock.mockClear());
    service.categories.mockImplementation(() => of(tree));
    service.suggestions.mockReset();
    service.suggestions.mockReturnValue(of([]));
    service.recipientUnits.mockReset();
    service.recipientUnits.mockReturnValue(of([]));
    dialog.open.mockReset();
    auth.userId.set('21');
    auth.isAuthenticated.set(true);
  });

  it('ac3_5_a_returned_draft_shows_the_reviewers_comment', async () => {
    service.get.mockReturnValue(
      of(
        award({
          request: {
            status: 'RETURNED',
            currentLevel: 'DEAN',
            submittedAt: '2026-09-28T09:00:00Z',
            deadline: null,
            estimatedCompletion: null,
            overdue: false,
            returnComment: 'Додайте номер наказу',
          },
        }),
      ),
    );
    await open('5');
    fixture.detectChanges();

    expect(component.returned()).toEqual({ comment: 'Додайте номер наказу' });
    expect(fixture.nativeElement.querySelector('[data-testid="award-returned"]')).not.toBeNull();
  });

  it('ac3_5_a_draft_never_returned_shows_no_banner', async () => {
    service.get.mockReturnValue(of(award()));
    await open('5');
    fixture.detectChanges();

    expect(component.returned()).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="award-returned"]')).toBeNull();
  });

  it('ac0_8_offers_no_recipient_choice_to_anyone_without_units', async () => {
    await open(null);

    expect(fixture.nativeElement.querySelector('[data-testid="award-recipient"]')).toBeNull();
  });

  it('ac0_8_a_secretary_enters_a_department_award', async () => {
    service.recipientUnits.mockReturnValue(of(units));
    await open(null);
    service.create.mockReturnValue(created(award()));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="award-recipient"]')).not.toBeNull();
    type('titleUk', 'Грамота кафедрі');
    type('recipient', 'UNIT');
    type('recipientOrganizationId', 64);

    component.save();

    expect(service.create).toHaveBeenCalledWith(
      expect.objectContaining({ titleUk: 'Грамота кафедрі', recipientOrganizationId: 64 }),
      expect.any(String),
    );
  });

  it('ac0_8_switching_back_to_me_clears_the_unit', async () => {
    service.recipientUnits.mockReturnValue(of(units));
    await open(null);
    service.create.mockReturnValue(created(award()));
    type('titleUk', 'Грамота');
    type('recipient', 'UNIT');
    type('recipientOrganizationId', 64);
    type('recipient', 'PERSON');

    component.save();

    expect(component.form.controls.recipientOrganizationId.value).toBeNull();
    expect(service.create).toHaveBeenCalledWith(
      expect.objectContaining({ recipientOrganizationId: null }),
      expect.any(String),
    );
  });

  it('ac0_8_a_unit_choice_without_a_unit_is_marked_and_not_sent', async () => {
    service.recipientUnits.mockReturnValue(of(units));
    await open(null);
    type('titleUk', 'Грамота');
    type('recipient', 'UNIT');

    component.save();

    expect(service.create).not.toHaveBeenCalled();
    expect(component.errorKey('recipientOrganizationId')).toBe('awards.errors.required');
  });

  it('ac0_3_a_unit_refused_by_the_server_is_shown_on_the_picker', async () => {
    service.recipientUnits.mockReturnValue(of(units));
    await open(null);
    service.create.mockReturnValue(
      throwError(() =>
        problem('validation-failed', 422, [
          { field: 'recipientOrganizationId', code: 'out-of-scope', message: 'x' },
        ]),
      ),
    );
    type('titleUk', 'Грамота');
    type('recipient', 'UNIT');
    type('recipientOrganizationId', 64);

    component.save();

    expect(component.errorKey('recipientOrganizationId')).toBe('awards.errors.out-of-scope');
  });

  it('ac0_4_a_unit_draft_keeps_its_unit_after_the_role_is_gone', async () => {
    const unit = {
      id: 70,
      name: 'Geometry',
      nameUk: 'Кафедра геометрії',
      type: 'DEPARTMENT' as const,
    };
    service.get.mockReturnValue(of(award({ recipient: { type: 'UNIT', organization: unit } })));
    await open('5');

    expect(component.form.controls.recipient.value).toBe('UNIT');
    expect(component.form.controls.recipientOrganizationId.value).toBe(70);
    expect(component.unitOptions()).toEqual([unit]);
  });

  it('ac1_1_saves_a_draft_with_only_a_title_and_moves_to_its_address', async () => {
    await open(null);
    service.create.mockReturnValue(created(award()));
    type('titleUk', '  Грамота  ');

    component.save();

    expect(service.create).toHaveBeenCalledWith(
      expect.objectContaining({ titleUk: 'Грамота', title: null }),
      expect.any(String),
    );
    expect(location.replaceState).toHaveBeenCalledWith('/awards/5/edit');
    expect(component.message()).toBe('awards.messages.saved');
    expect(component.form.dirty).toBe(false);
  });

  it('ac1_1_does_not_send_a_form_without_any_title', async () => {
    await open(null);

    component.save();

    expect(service.create).not.toHaveBeenCalled();
    expect(component.form.hasError('titleRequired')).toBe(true);
  });

  it('ac1_5_marks_the_fields_a_submission_needs_and_sends_nothing', async () => {
    await open(null);
    type('titleUk', 'Грамота');

    component.submit();

    expect(service.create).not.toHaveBeenCalled();
    expect(component.errorKey('categoryId')).toBe('awards.errors.required');
    expect(component.errorKey('awardingOrganization')).toBe('awards.errors.required');
    expect(component.errorKey('awardDate')).toBe('awards.errors.required');
    expect(component.problem()).toBe('awards.problems.award-incomplete');
  });

  it('m1_a_draft_is_saved_after_a_submission_that_lacked_fields', async () => {
    await open(null);
    service.create.mockReturnValue(created(award()));
    type('titleUk', 'Грамота');
    component.submit();

    component.save();

    expect(service.create).toHaveBeenCalledTimes(1);
    expect(component.errorKey('categoryId')).toBeNull();
    expect(component.problem()).toBeNull();
  });

  it('m1_a_file_is_uploaded_after_a_submission_that_lacked_fields', async () => {
    await open(null);
    service.create.mockReturnValue(created(award()));
    type('titleUk', 'Грамота');
    component.submit();
    let id: number | undefined;

    component.prepareUpload().subscribe((saved) => (id = saved));

    expect(id).toBe(5);
  });

  it('m1_a_draft_refused_for_another_reason_drops_the_submission_notice', async () => {
    await open(null);
    type('titleUk', 'Грамота');
    component.submit();
    type('externalUrl', 'mon.gov.ua');

    component.save();

    expect(service.create).not.toHaveBeenCalled();
    expect(component.problem()).toBeNull();
    expect(component.errorKey('externalUrl')).toBe('awards.errors.invalid');
  });

  it('m2_cancel_leads_back_to_the_list_from_a_new_form', async () => {
    await open(null);
    const cancel: HTMLAnchorElement = fixture.nativeElement.querySelector(
      '[data-testid="award-cancel"]',
    );

    expect(cancel.getAttribute('href')).toBe('/awards');
  });

  it('m2_cancel_leads_back_to_the_draft_from_its_edit_page', async () => {
    service.get.mockReturnValue(of(award()));
    await open('5');
    fixture.detectChanges();
    const cancel: HTMLAnchorElement = fixture.nativeElement.querySelector(
      '[data-testid="award-cancel"]',
    );

    expect(cancel.getAttribute('href')).toBe('/awards/5');
  });

  it('ac1_5_ac1_9_saves_then_submits_and_shows_the_confirmation', async () => {
    await open(null);
    service.create.mockReturnValue(created(award({ version: 2 })));
    service.submit.mockReturnValue(of(award({ status: 'PENDING', version: 3 })));
    type('titleUk', 'Грамота');
    type('categoryId', 13);
    type('awardingOrganization', 'МОН України');
    type('awardDate', '2025-05-01');

    component.submit();

    expect(service.submit).toHaveBeenCalledWith(5, 2);
    expect(router.navigate).toHaveBeenCalledWith(['/awards', 5, 'submitted'], { replaceUrl: true });
  });

  it('ac2_6_the_date_picker_allows_neither_future_dates_nor_dates_older_than_fifty_years', async () => {
    await open(null);
    const input = fixture.nativeElement.querySelector(
      '[data-testid="award-date"]',
    ) as HTMLInputElement;

    expect(input.max).toBe(component.today);
    expect(input.min).toBe(
      `${Number(component.today.substring(0, 4)) - 50}${component.today.substring(4)}`,
    );
  });

  it('ac2_3_a_recent_date_shows_the_hint_under_the_date', async () => {
    await open(null);
    type('awardDate', component.today);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="award-recent-date"]')).not.toBeNull();

    type('awardDate', '2020-01-01');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="award-recent-date"]')).toBeNull();
  });

  it('ac2_4_a_saved_possible_duplicate_links_the_matching_award', async () => {
    const match = {
      id: 9,
      title: null,
      titleUk: 'Грамота МОН',
      awardDate: '2025-05-01',
      status: 'PENDING' as const,
    };
    service.get.mockReturnValue(
      of(award({ warnings: [{ code: 'POSSIBLE_DUPLICATE', field: 'title', matches: [match] }] })),
    );
    await open('5');

    const link = fixture.nativeElement.querySelector(
      '[data-testid="award-duplicate-9"]',
    ) as HTMLAnchorElement;
    expect(link.textContent).toContain('Грамота МОН');
    expect(link.getAttribute('href')).toBe('/awards/9');

    type('awardDate', '2024-01-01');
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="award-duplicate-warning"]'),
    ).toBeNull();
  });

  it('ac2_5_a_possible_duplicate_is_submitted_after_the_owner_confirms_it', async () => {
    const match = {
      id: 9,
      title: null,
      titleUk: 'Грамота МОН',
      awardDate: '2025-05-01',
      status: 'PENDING',
    };
    await open(null);
    service.create.mockReturnValue(created(award({ version: 2 })));
    service.submit
      .mockReturnValueOnce(throwError(() => duplicateOf([match])))
      .mockReturnValueOnce(of(award({ status: 'PENDING', version: 3 })));
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    fillComplete();

    component.submit();

    expect(dialog.open).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ data: { matches: [match] } }),
    );
    expect(service.submit).toHaveBeenLastCalledWith(5, 2, true);
    expect(router.navigate).toHaveBeenCalledWith(['/awards', 5, 'submitted'], { replaceUrl: true });
  });

  it('ac2_5_cancelling_the_duplicate_dialog_leaves_a_saved_draft', async () => {
    await open(null);
    service.create.mockReturnValue(created(award({ version: 2 })));
    service.submit.mockReturnValue(throwError(() => duplicateOf([{ id: 9 }])));
    dialog.open.mockReturnValue({ afterClosed: () => of(false) });
    fillComplete();

    component.submit();

    expect(service.submit).toHaveBeenCalledTimes(1);
    expect(component.saving()).toBe(false);
    expect(component.message()).toBe('awards.messages.saved');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('ac1_3_a_stale_version_offers_a_reload_and_keeps_the_typed_values', async () => {
    service.get.mockReturnValue(of(award()));
    await open('5');
    service.update.mockReturnValue(throwError(() => problem('award-stale', 409)));
    type('titleUk', 'Моя версія');

    component.save();
    fixture.detectChanges();

    expect(service.update).toHaveBeenCalledWith(
      5,
      expect.objectContaining({ titleUk: 'Моя версія' }),
      1,
    );
    expect(component.stale()).toBe(true);
    expect(fixture.nativeElement.querySelector('[data-testid="award-reload"]')).not.toBeNull();
    expect(copies.load('21', 'award-5')).toEqual(
      expect.objectContaining({ titleUk: 'Моя версія' }),
    );

    service.get.mockReturnValue(of(award({ titleUk: 'Інша версія', version: 2 })));
    component.reload();

    expect(component.form.controls.titleUk.value).toBe('Інша версія');
    expect(component.restoreOffer()).toEqual(expect.objectContaining({ titleUk: 'Моя версія' }));
  });

  it('ac1_6_an_award_submitted_elsewhere_switches_to_the_read_only_view', async () => {
    service.get.mockReturnValue(of(award()));
    await open('5');
    service.update.mockReturnValue(throwError(() => problem('award-not-editable', 409)));
    type('titleUk', 'Грамота');
    type('categoryId', 13);
    type('awardingOrganization', 'МОН');
    type('awardDate', '2025-05-01');

    component.submit();

    expect(router.navigate).toHaveBeenCalledWith(['/awards', 5], {
      state: { problem: 'award-not-editable' },
    });
  });

  it('ac1_1_shows_the_field_errors_of_the_server', async () => {
    await open(null);
    service.create.mockReturnValue(
      throwError(() =>
        problem('validation-failed', 422, [{ field: 'awardDate', code: 'future', message: 'x' }]),
      ),
    );
    type('titleUk', 'Грамота');

    component.save();

    expect(component.errorKey('awardDate')).toBe('awards.errors.future');
    expect(component.problem()).toBe('awards.problems.validation-failed');
  });

  it('ac1_10_an_unreachable_server_keeps_the_values', async () => {
    await open(null);
    service.create.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    type('titleUk', 'Грамота');

    component.save();

    expect(component.problem()).toBeNull();
    expect(component.pending()).toBe(true);
    expect(component.form.controls.titleUk.value).toBe('Грамота');
    expect(component.saving()).toBe(false);
  });

  it('ac1_10_offers_the_copy_of_an_interrupted_form_back', async () => {
    copies.save('21', 'award-new', { ...emptyForm(), titleUk: 'Незбережена' });
    await open(null);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="restore-offer"]')).not.toBeNull();
    component.restore();

    expect(component.form.controls.titleUk.value).toBe('Незбережена');
    expect(component.restoreOffer()).toBeNull();
  });

  it('ac1_10_discarding_the_copy_removes_it', async () => {
    copies.save('21', 'award-new', { ...emptyForm(), titleUk: 'Незбережена' });
    await open(null);

    component.discard();

    expect(copies.load('21', 'award-new')).toBeNull();
  });

  it('ac1_10_closing_the_tab_keeps_a_copy_of_the_typed_values', async () => {
    await open(null);
    type('titleUk', 'Половина');
    const event = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent;

    component.keepOnUnload(event);

    expect(copies.load('21', 'award-new')).toEqual(
      expect.objectContaining({ titleUk: 'Половина' }),
    );
    expect(event.defaultPrevented).toBe(true);
  });

  it('f4_an_ended_session_keeps_the_copy_for_its_user_without_holding_the_page', async () => {
    await open(null);
    type('titleUk', 'Половина');
    auth.userId.set(null);
    auth.isAuthenticated.set(false);
    const event = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent;

    component.keepOnUnload(event);

    expect(copies.load('21', 'award-new')).toEqual(
      expect.objectContaining({ titleUk: 'Половина' }),
    );
    expect(event.defaultPrevented).toBe(false);
  });

  it('f4_signing_out_with_a_filled_form_neither_keeps_a_copy_nor_holds_the_page', async () => {
    await open(null);
    type('titleUk', 'Половина');
    copies.clearAll();
    const event = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent;

    component.keepOnUnload(event);

    expect(copies.load('21', 'award-new')).toBeNull();
    expect(event.defaultPrevented).toBe(false);
  });

  it('f2_a_proxy_answer_while_the_server_restarts_counts_as_unreachable', async () => {
    await open(null);
    service.create.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 502, error: '<html>Bad Gateway</html>' })),
    );
    type('titleUk', 'Грамота');

    component.save();

    expect(component.problem()).toBe('awards.problems.network');
    expect(component.form.controls.titleUk.value).toBe('Грамота');
  });

  it('f3_a_draft_deleted_in_another_window_becomes_a_new_unsaved_draft', async () => {
    service.get.mockReturnValue(of(award()));
    await open('5');
    copies.save('21', 'award-5', emptyForm());
    service.update.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    type('titleUk', 'Змінена грамота');

    component.save();

    expect(component.problem()).toBe('awards.problems.award-deleted');
    expect(component.editing()).toBe(false);
    expect(location.replaceState).toHaveBeenCalledWith('/awards/new');
    expect(copies.load('21', 'award-5')).toBeNull();
    expect(copies.load('21', 'award-new')).toEqual(
      expect.objectContaining({ titleUk: 'Змінена грамота' }),
    );

    service.create.mockReturnValue(created(award({ id: 6, titleUk: 'Змінена грамота' })));
    component.save();

    expect(service.create).toHaveBeenCalledWith(
      expect.objectContaining({ titleUk: 'Змінена грамота' }),
      expect.any(String),
    );
    expect(location.replaceState).toHaveBeenCalledWith('/awards/6/edit');
  });

  it('f9_a_draft_is_deleted_after_confirmation_and_the_list_is_shown', async () => {
    service.get.mockReturnValue(of(award()));
    await open('5');
    fixture.detectChanges();
    copies.save('21', 'award-5', emptyForm());
    service.remove.mockReturnValue(of(undefined));

    dialog.open.mockReturnValue({ afterClosed: () => of(false) });
    (
      fixture.nativeElement.querySelector('[data-testid="award-remove"]') as HTMLButtonElement
    ).click();
    expect(service.remove).not.toHaveBeenCalled();

    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    component.remove();

    expect(service.remove).toHaveBeenCalledWith(5);
    expect(copies.load('21', 'award-5')).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/awards'], {
      replaceUrl: true,
      state: { notice: 'awards.messages.removed' },
    });
    expect(component.confirmLeave()).toBe(true);
  });

  it('f9_a_returned_draft_offers_no_delete', async () => {
    service.get.mockReturnValue(
      of(
        award({
          request: {
            status: 'RETURNED',
            currentLevel: 'DEAN',
            submittedAt: '2026-09-28T09:00:00Z',
            deadline: null,
            estimatedCompletion: null,
            overdue: false,
            returnComment: 'Додайте номер наказу',
          },
        }),
      ),
    );
    await open('5');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="award-remove"]')).toBeNull();
    component.remove();
    expect(dialog.open).not.toHaveBeenCalled();
  });

  it('f9_a_new_form_has_nothing_to_delete', async () => {
    await open(null);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="award-remove"]')).toBeNull();
    component.remove();
    expect(dialog.open).not.toHaveBeenCalled();
  });

  it('f9_a_draft_already_deleted_elsewhere_counts_as_deleted', async () => {
    service.get.mockReturnValue(of(award()));
    await open('5');
    service.remove.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });

    component.remove();

    expect(router.navigate).toHaveBeenCalledWith(
      ['/awards'],
      expect.objectContaining({ replaceUrl: true }),
    );
  });

  it('f9_a_submitted_draft_is_not_deleted_and_opens_read_only', async () => {
    service.get.mockReturnValue(of(award()));
    await open('5');
    service.remove.mockReturnValue(throwError(() => problem('award-not-editable', 409)));
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });

    component.remove();

    expect(router.navigate).toHaveBeenCalledWith(['/awards', 5], {
      state: { problem: 'award-not-editable' },
    });
  });

  it('ac1_10_leaving_with_unsaved_values_asks_first', async () => {
    await open(null);
    expect(component.confirmLeave()).toBe(true);

    type('titleUk', 'Половина');
    copies.save('21', 'award-new', emptyForm());
    dialog.open.mockReturnValue({ afterClosed: () => of(false) });
    let stay: boolean | undefined;
    (component.confirmLeave() as Observable<boolean>).subscribe((answer) => (stay = answer));
    expect(stay).toBe(false);

    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    let leave: boolean | undefined;
    (component.confirmLeave() as Observable<boolean>).subscribe((answer) => (leave = answer));
    expect(leave).toBe(true);
    expect(copies.load('21', 'award-new')).toBeNull();
  });

  it('ac1_a_confirmed_leave_neither_asks_again_nor_keeps_a_copy_on_unload', async () => {
    await open(null);
    type('titleUk', 'Половина');
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    (component.confirmLeave() as Observable<boolean>).subscribe();
    dialog.open.mockClear();
    const event = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent;

    expect(component.confirmLeave()).toBe(true);
    component.keepOnUnload(event);

    expect(dialog.open).not.toHaveBeenCalled();
    expect(copies.load('21', 'award-new')).toBeNull();
    expect(event.defaultPrevented).toBe(false);
  });

  it('ac1_9_a_submitted_award_opens_read_only', async () => {
    service.get.mockReturnValue(of(award({ status: 'PENDING' })));
    await open('5');

    expect(router.navigate).toHaveBeenCalledWith(['/awards', 5], { replaceUrl: true });
  });

  it('ac1_8_an_unknown_or_malformed_id_is_not_found', async () => {
    await open('abc');
    expect(component.notFound()).toBe(true);
    expect(service.get).not.toHaveBeenCalled();
  });

  it('ac1_8_a_hidden_award_is_not_found', async () => {
    service.get.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    await open('999');

    expect(component.notFound()).toBe(true);
  });

  it('edge_a_deactivated_category_stays_visible_on_its_draft', async () => {
    service.get.mockReturnValue(
      of(award({ category: { id: 44, name: 'Old', nameUk: 'Стара категорія', level: 'LOCAL' } })),
    );
    await open('5');

    expect(component.retiredCategory()?.id).toBe(44);
    expect(component.form.controls.categoryId.value).toBe(44);
  });

  describe('category suggestions', () => {
    const ministry: CategorySuggestion = {
      id: 13,
      name: 'Ministry Recognition',
      nameUk: 'Відзнака міністерства',
      level: 'NATIONAL',
      score: 110,
      reasons: ['KEYWORD', 'HISTORY'],
    };

    function chips(): HTMLElement[] {
      return Array.from(
        fixture.nativeElement.querySelectorAll('[data-testid^="category-suggestion-"]'),
      );
    }

    async function openWithTimers(): Promise<void> {
      await open(null);
      vi.useFakeTimers();
    }

    afterEach(() => vi.useRealTimers());

    it('ac3_4_asks_once_the_title_and_organisation_rest_for_400_ms_and_shows_chips', async () => {
      service.suggestions.mockReturnValue(of([ministry]));
      await openWithTimers();
      type('titleUk', 'Грамота');
      type('title', 'Letter');
      type('awardingOrganization', 'Міністерство освіти');

      vi.advanceTimersByTime(399);
      expect(service.suggestions).not.toHaveBeenCalled();
      vi.advanceTimersByTime(1);
      fixture.detectChanges();

      expect(service.suggestions).toHaveBeenCalledTimes(1);
      expect(service.suggestions).toHaveBeenCalledWith('Грамота Letter', 'Міністерство освіти');
      expect(chips().map((chip) => chip.dataset['testid'])).toEqual(['category-suggestion-13']);
      expect(chips()[0].textContent).toContain('Відзнака міністерства');
      expect(chips()[0].textContent).toContain('categories.levels.NATIONAL');
      expect(chips()[0].textContent).toContain('categories.reasons.HISTORY');
    });

    it('ac3_4_choosing_a_chip_fills_the_category_and_hides_the_chips', async () => {
      service.suggestions.mockReturnValue(of([ministry]));
      await openWithTimers();
      type('titleUk', 'Грамота МОН');
      vi.advanceTimersByTime(400);
      fixture.detectChanges();

      chips()[0].click();
      fixture.detectChanges();

      expect(component.form.controls.categoryId.value).toBe(13);
      expect(component.form.dirty).toBe(true);
      expect(chips()).toHaveLength(0);
    });

    it('ac3_4_a_category_the_user_picked_is_never_replaced', async () => {
      service.suggestions.mockReturnValue(of([ministry]));
      await openWithTimers();
      type('categoryId', 10);
      type('titleUk', 'Грамота МОН');
      vi.advanceTimersByTime(400);
      fixture.detectChanges();

      expect(component.form.controls.categoryId.value).toBe(10);
      expect(chips()).toHaveLength(0);
    });

    it('ac3_4_no_chips_when_the_service_fails_and_later_input_still_asks', async () => {
      service.suggestions.mockReturnValueOnce(
        throwError(() => new HttpErrorResponse({ status: 500 })),
      );
      service.suggestions.mockReturnValueOnce(of([ministry]));
      await openWithTimers();
      type('titleUk', 'Грамота МОН');
      vi.advanceTimersByTime(400);
      fixture.detectChanges();

      expect(chips()).toHaveLength(0);

      type('titleUk', 'Грамота МОН України');
      vi.advanceTimersByTime(400);
      fixture.detectChanges();

      expect(chips()).toHaveLength(1);
    });

    it('f5_long_texts_are_cut_before_they_are_sent', async () => {
      await openWithTimers();
      type('titleUk', 'Грамота '.repeat(60));
      type('awardingOrganization', 'М'.repeat(255));
      vi.advanceTimersByTime(400);

      const [title, organization] = service.suggestions.mock.calls[0] as unknown as [
        string,
        string,
      ];
      expect(title.length).toBe(300);
      expect(organization.length).toBe(255);
    });

    it('ac3_1_short_inputs_are_not_sent_and_clear_the_chips', async () => {
      service.suggestions.mockReturnValue(of([ministry]));
      await openWithTimers();
      type('titleUk', 'Грамота МОН');
      vi.advanceTimersByTime(400);
      type('titleUk', 'Гр');
      vi.advanceTimersByTime(400);
      fixture.detectChanges();

      expect(service.suggestions).toHaveBeenCalledTimes(1);
      expect(chips()).toHaveLength(0);
    });
  });

  describe('documents', () => {
    it('ac2_1_form_has_the_documents_section_in_place_of_the_photo_note', async () => {
      await open(null);

      expect(fixture.nativeElement.querySelector('[data-testid="award-documents"]')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('[data-testid="documents-drop"]')).not.toBeNull();
    });

    it('ac2_2_first_file_of_a_new_award_saves_the_draft_first', async () => {
      await open(null);
      service.create.mockReturnValue(created(award({ id: 42, version: 1 })));
      type('titleUk', 'Грамота');

      let saved: number | undefined;
      component.prepareUpload().subscribe((id) => (saved = id));

      expect(service.create).toHaveBeenCalledTimes(1);
      expect(saved).toBe(42);
      expect(location.replaceState).toHaveBeenCalledWith('/awards/42/edit');
      expect(component.message()).toBe('awards.messages.saved');
      expect(component.form.pristine).toBe(true);
    });

    it('ac2_2_invalid_form_is_not_saved_and_the_upload_waits', async () => {
      await open(null);

      let failed = false;
      component.prepareUpload().subscribe({ error: () => (failed = true) });

      expect(failed).toBe(true);
      expect(service.create).not.toHaveBeenCalled();
      expect(component.form.hasError('titleRequired')).toBe(true);
    });

    it('ac2_2_save_error_is_shown_like_a_save_of_the_button', async () => {
      await open(null);
      service.create.mockReturnValue(throwError(() => problem('validation-failed', 422)));
      type('titleUk', 'Грамота');

      let failed = false;
      component.prepareUpload().subscribe({ error: () => (failed = true) });

      expect(failed).toBe(true);
      expect(component.saving()).toBe(false);
      expect(component.problem()).toBe('awards.problems.validation-failed');
    });

    it('finding3_a_file_added_while_the_draft_is_saving_waits_for_that_save', async () => {
      await open(null);
      const response = new Subject<AwardCreation>();
      service.create.mockReturnValue(response);
      type('titleUk', 'Грамота');

      component.save();
      let saved: number | undefined;
      let failed = false;
      component.prepareUpload().subscribe({
        next: (id) => (saved = id),
        error: () => (failed = true),
      });
      response.next({ award: award({ id: 42, version: 1 }), repeated: false });
      response.complete();

      expect(service.create).toHaveBeenCalledTimes(1);
      expect(failed).toBe(false);
      expect(saved).toBe(42);
      expect(component.message()).toBe('awards.messages.saved');
    });

    it('finding3_a_failed_running_save_fails_the_waiting_file_too', async () => {
      await open(null);
      const response = new Subject<AwardCreation>();
      service.create.mockReturnValue(response);
      type('titleUk', 'Грамота');

      component.save();
      let failed = false;
      component.prepareUpload().subscribe({ error: () => (failed = true) });
      response.error(problem('validation-failed', 422));

      expect(failed).toBe(true);
      expect(component.saving()).toBe(false);
      expect(component.problem()).toBe('awards.problems.validation-failed');
    });

    it('finding1_upload_to_a_draft_deleted_elsewhere_makes_the_values_a_new_draft', async () => {
      service.get.mockReturnValue(of(award()));
      await open('5');

      component.message.set('awards.messages.saved');

      component.documentsLost(new HttpErrorResponse({ status: 404 }));

      expect(component.message()).toBeNull();
      expect(component.problem()).toBe('awards.problems.award-deleted');
      expect(component.editing()).toBe(false);
      expect(location.replaceState).toHaveBeenCalledWith('/awards/new');
    });

    it('finding2_upload_to_a_draft_submitted_elsewhere_opens_the_award_page', async () => {
      service.get.mockReturnValue(of(award()));
      await open('5');

      component.documentsLost(problem('award-not-editable', 409));

      expect(router.navigate).toHaveBeenCalledWith(['/awards', 5], {
        state: { problem: 'award-not-editable' },
      });
    });

    it('finding1_a_refusal_during_a_running_save_is_left_to_that_save', async () => {
      service.get.mockReturnValue(of(award()));
      await open('5');
      service.update.mockReturnValue(new Subject<Award>());
      type('titleUk', 'Грамота');
      component.save();

      component.documentsLost(new HttpErrorResponse({ status: 404 }));

      expect(component.saving()).toBe(true);
      expect(component.editing()).toBe(true);
    });

    it('ac2_8_submitting_without_documents_asks_with_the_notice', async () => {
      await open(null);
      component.documentCount.set(0);
      service.create.mockReturnValue(created(award({ version: 2 })));
      service.submit.mockReturnValue(of(award({ status: 'PENDING', version: 3 })));
      dialog.open.mockReturnValue({ afterClosed: () => of(true) });
      fillComplete();

      component.submit();

      expect(dialog.open.mock.calls[0][1].data).toMatchObject({
        text: 'awards.submitWithout.text',
      });
      expect(service.submit).toHaveBeenCalledWith(5, 2);
    });

    it('ac2_8_cancelling_the_notice_sends_nothing', async () => {
      await open(null);
      component.documentCount.set(0);
      dialog.open.mockReturnValue({ afterClosed: () => of(false) });
      fillComplete();

      component.submit();

      expect(service.create).not.toHaveBeenCalled();
      expect(component.saving()).toBe(false);
    });

    it('ac2_8_submitting_with_documents_asks_nothing', async () => {
      await open(null);
      service.create.mockReturnValue(created(award({ version: 2 })));
      service.submit.mockReturnValue(of(award({ status: 'PENDING', version: 3 })));
      fillComplete();

      component.submit();

      expect(dialog.open).not.toHaveBeenCalled();
      expect(service.submit).toHaveBeenCalledTimes(1);
    });
  });

  it('ac3_3_the_web_link_opens_the_url_keyboard', async () => {
    await open(null);

    const link = fixture.nativeElement.querySelector('[data-testid="award-url"]') as HTMLElement;

    expect(link.getAttribute('inputmode')).toBe('url');
    expect(link.getAttribute('type')).toBe('url');
  });

  describe('offline draft', () => {
    let connectivity: ConnectivityService;

    function goOffline(): void {
      connectivity = TestBed.inject(ConnectivityService);
      vi.spyOn(connectivity, 'linked').mockReturnValue(false);
      connectivity.unreachable();
      fixture.detectChanges();
    }

    function goOnline(): void {
      vi.mocked(connectivity.linked).mockReturnValue(true);
      connectivity.reachable();
      fixture.detectChanges();
    }

    function text(testId: string): string | null {
      const element = fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
      return element ? element.textContent.trim() : null;
    }

    it('ac2_1_the_form_shows_the_offline_notice_and_stays_editable', async () => {
      await open(null);

      goOffline();

      expect(text('award-offline-notice')).toBe('awards.offline.notice');
      expect(component.form.enabled).toBe(true);
    });

    it('ac2_2_saving_offline_sends_nothing_and_keeps_the_save_pending', async () => {
      await open(null);
      goOffline();
      type('titleUk', 'Грамота');

      component.save();
      fixture.detectChanges();

      expect(service.create).not.toHaveBeenCalled();
      expect(text('award-offline-notice')).toBe('awards.offline.pending');
      expect(copies.load('21', 'award-new')).toEqual(
        expect.objectContaining({ titleUk: 'Грамота' }),
      );
    });

    it('ac2_2_a_form_without_a_title_is_marked_and_not_kept_pending', async () => {
      await open(null);
      goOffline();
      type('awardingOrganization', 'МОН України');

      component.save();

      expect(component.pending()).toBe(false);
      expect(component.form.controls.title.touched).toBe(true);
    });

    it('ac2_3_a_new_award_saved_offline_is_created_once_on_reconnect', async () => {
      await open(null);
      goOffline();
      type('titleUk', 'Грамота');
      component.save();
      service.create.mockReturnValue(created(award({ id: 7 })));

      goOnline();

      expect(service.create).toHaveBeenCalledTimes(1);
      expect(location.replaceState).toHaveBeenCalledWith('/awards/7/edit');
      expect(component.message()).toBe('awards.messages.reconnected');
      expect(copies.load('21', 'award-new')).toBeNull();
      expect(text('award-offline-notice')).toBeNull();
    });

    it('ac2_3_changes_to_a_draft_made_offline_are_saved_with_its_version', async () => {
      service.get.mockReturnValue(of(award({ version: 4 })));
      await open('5');
      goOffline();
      type('titleUk', 'Зміна без мережі');
      service.update.mockReturnValue(of(award({ titleUk: 'Зміна без мережі', version: 5 })));

      goOnline();

      expect(service.update).toHaveBeenCalledWith(
        5,
        expect.objectContaining({ titleUk: 'Зміна без мережі' }),
        4,
      );
      expect(component.message()).toBe('awards.messages.reconnected');
      expect(copies.load('21', 'award-5')).toBeNull();
    });

    it('ac2_3_changes_made_before_the_connection_dropped_wait_for_a_save', async () => {
      service.get.mockReturnValue(of(award()));
      await open('5');
      type('titleUk', 'Зміна з мережею');
      goOffline();

      goOnline();

      expect(service.update).not.toHaveBeenCalled();
    });

    it('ac2_3_a_form_that_lost_its_title_asks_for_one_and_sends_nothing', async () => {
      await open(null);
      goOffline();
      type('titleUk', 'Грамота');
      component.save();
      type('titleUk', '');

      goOnline();

      expect(service.create).not.toHaveBeenCalled();
      expect(text('award-offline-notice')).toBe('awards.offline.titleNeeded');
    });

    it('ac2_4_submit_and_documents_wait_for_the_connection', async () => {
      await open(null);
      goOffline();
      fillComplete();

      component.submit();
      fixture.detectChanges();

      expect(service.create).not.toHaveBeenCalled();
      expect(dialog.open).not.toHaveBeenCalled();
      expect(text('award-offline-submit')).toBe('awards.offline.submit');
      expect(text('documents-hint')).toBe('awards.documents.offline');

      service.create.mockReturnValue(created(award()));
      goOnline();

      expect(text('award-offline-submit')).toBeNull();
      expect(text('documents-hint')).toBe('awards.documents.hint');
    });

    it('ac2_5_a_draft_changed_elsewhere_meets_the_stale_outcome_on_reconnect', async () => {
      service.get.mockReturnValue(of(award()));
      await open('5');
      goOffline();
      type('titleUk', 'Моя версія');
      service.update.mockReturnValue(throwError(() => problem('award-stale', 409)));

      goOnline();

      expect(component.stale()).toBe(true);
      expect(component.problem()).toBe('awards.problems.award-stale');
      expect(copies.load('21', 'award-5')).toEqual(
        expect.objectContaining({ titleUk: 'Моя версія' }),
      );
    });

    it('ac2_5_a_draft_deleted_elsewhere_becomes_a_new_unsaved_draft_on_reconnect', async () => {
      service.get.mockReturnValue(of(award()));
      await open('5');
      goOffline();
      type('titleUk', 'Моя версія');
      service.update.mockReturnValue(throwError(() => problem('not-found', 404)));

      goOnline();

      expect(component.problem()).toBe('awards.problems.award-deleted');
      expect(location.replaceState).toHaveBeenCalledWith('/awards/new');
    });

    it('ac2_5_a_draft_submitted_elsewhere_opens_the_award_page_on_reconnect', async () => {
      service.get.mockReturnValue(of(award()));
      await open('5');
      goOffline();
      type('titleUk', 'Моя версія');
      service.update.mockReturnValue(throwError(() => problem('award-not-editable', 409)));

      goOnline();

      expect(router.navigate).toHaveBeenCalledWith(['/awards', 5], {
        state: { problem: 'award-not-editable' },
      });
    });

    it('ac2_6_a_copy_restored_offline_is_saved_on_reconnect', async () => {
      copies.save('21', 'award-new', { ...emptyForm(), titleUk: 'Незбережена' });
      await open(null);
      goOffline();
      component.restore();
      service.create.mockReturnValue(created(award({ id: 8 })));

      goOnline();

      expect(service.create).toHaveBeenCalledWith(
        expect.objectContaining({ titleUk: 'Незбережена' }),
        expect.any(String),
      );
    });

    it('ac2_8_a_session_ended_elsewhere_keeps_the_copy', async () => {
      await open(null);
      goOffline();
      type('titleUk', 'Грамота');
      component.save();
      service.create.mockReturnValue(throwError(() => problem('unauthorized', 401)));

      goOnline();

      expect(copies.load('21', 'award-new')).toEqual(
        expect.objectContaining({ titleUk: 'Грамота' }),
      );
    });

    it('ac2_9_a_save_without_an_answer_turns_pending_and_runs_once_when_a_request_succeeds', async () => {
      await open(null);
      connectivity = TestBed.inject(ConnectivityService);
      service.create.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })));
      type('titleUk', 'Грамота');

      component.save();
      fixture.detectChanges();

      expect(component.problem()).toBeNull();
      expect(connectivity.online()).toBe(false);
      expect(text('award-offline-notice')).toBe('awards.offline.pending');

      service.create.mockReturnValue(created(award({ id: 9 })));
      connectivity.reachable();
      connectivity.reachable();

      expect(service.create).toHaveBeenCalledTimes(2);
      expect(component.message()).toBe('awards.messages.reconnected');
    });

    it('ac2_9_saving_again_while_the_browser_is_linked_sends_the_request', async () => {
      await open(null);
      connectivity = TestBed.inject(ConnectivityService);
      service.create.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })));
      type('titleUk', 'Грамота');
      component.save();
      service.create.mockReturnValue(created(award({ id: 9 })));

      component.save();

      expect(service.create).toHaveBeenCalledTimes(2);
      expect(component.pending()).toBe(false);
    });

    it('ac4_1_a_save_whose_answer_was_lost_is_repeated_with_the_same_key', async () => {
      await open(null);
      connectivity = TestBed.inject(ConnectivityService);
      service.create.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })));
      type('titleUk', 'Грамота');
      component.save();
      const key = service.create.mock.calls[0][1];
      expect(copies.load('21', NEW_KEY)).toBe(key);
      service.create.mockReturnValue(created(award({ id: 9 })));

      connectivity.reachable();

      expect(service.create).toHaveBeenLastCalledWith(expect.anything(), key);
      expect(copies.load('21', NEW_KEY)).toBeNull();
    });

    it('ac4_1_a_repeat_of_a_draft_deleted_meanwhile_becomes_a_new_draft_with_a_new_key', async () => {
      copies.save('21', 'award-new', { ...emptyForm(), titleUk: 'Незбережена' });
      copies.save('21', NEW_KEY, STORED_KEY);
      await open(null);
      component.restore();
      service.create.mockReturnValueOnce(throwError(() => problem('award-not-found', 404)));

      component.save();

      expect(component.problem()).toBe('awards.problems.award-deleted');
      expect(copies.load('21', NEW_KEY)).toBeNull();
      service.create.mockReturnValue(created(award({ id: 11 })));
      component.save();
      expect(service.create).toHaveBeenLastCalledWith(expect.anything(), expect.any(String));
      expect(service.create.mock.calls[1][1]).not.toBe(STORED_KEY);
    });

    it('ac4_1_a_restored_copy_repeats_the_key_stored_with_it', async () => {
      copies.save('21', 'award-new', { ...emptyForm(), titleUk: 'Незбережена' });
      copies.save('21', NEW_KEY, STORED_KEY);
      await open(null);
      component.restore();
      service.create.mockReturnValue(created(award({ id: 8, titleUk: 'Незбережена' }), true));

      component.save();

      expect(service.create).toHaveBeenCalledWith(expect.anything(), STORED_KEY);
      expect(location.replaceState).toHaveBeenCalledWith('/awards/8/edit');
      expect(component.restoreOffer()).toBeNull();
    });

    it('ac4_2_a_form_typed_offline_takes_the_key_another_tab_stored', async () => {
      copies.save('21', NEW_KEY, STORED_KEY);
      await open(null);
      goOffline();
      type('titleUk', 'Друга вкладка');
      component.save();
      service.create.mockReturnValue(created(award({ id: 7 }), true));

      goOnline();

      expect(service.create).toHaveBeenCalledWith(expect.anything(), STORED_KEY);
    });

    it('ac4_2_a_repeated_creation_shows_the_award_and_offers_the_values_typed_here', async () => {
      await open(null);
      goOffline();
      type('titleUk', 'Друга вкладка');
      component.save();
      service.create.mockReturnValue(created(award({ id: 7, titleUk: 'Перша вкладка' }), true));

      goOnline();

      expect(location.replaceState).toHaveBeenCalledWith('/awards/7/edit');
      expect(component.form.controls.titleUk.value).toBe('Перша вкладка');
      expect(component.restoreOffer()).toEqual(
        expect.objectContaining({ titleUk: 'Друга вкладка' }),
      );
      expect(text('restore-offer')).not.toBeNull();
    });

    it('ac4_2_a_repeated_creation_with_the_same_values_offers_nothing', async () => {
      await open(null);
      goOffline();
      type('titleUk', 'Грамота');
      component.save();
      service.create.mockReturnValue(created(award({ id: 7 }), true));

      goOnline();

      expect(component.restoreOffer()).toBeNull();
    });

    it('ac4_2_a_form_saved_online_does_not_take_a_stored_key', async () => {
      copies.save('21', NEW_KEY, STORED_KEY);
      await open(null);
      type('titleUk', 'Інша грамота');
      service.create.mockReturnValue(created(award({ id: 10 })));

      component.save();

      expect(service.create).not.toHaveBeenCalledWith(expect.anything(), STORED_KEY);
      expect(copies.load('21', NEW_KEY)).toBe(STORED_KEY);
    });

    it('ac2_1_storage_that_refuses_the_copy_is_named_in_the_notice', async () => {
      await open(null);
      vi.spyOn(copies, 'save').mockReturnValue(false);
      goOffline();
      type('titleUk', 'Грамота');

      component.save();
      fixture.detectChanges();

      expect(text('award-offline-notice')).toBe('awards.offline.notStored');
    });

    it('ac1_a_draft_that_cannot_be_loaded_shows_try_again_instead_of_the_form', async () => {
      service.get.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));

      await open('5');

      expect(fixture.nativeElement.querySelector('form')).toBeNull();
      expect(text('award-load-error')).toContain('awards.form.loadProblems.network');
      expect(text('award-load-retry')).toBe('awards.form.retry');
      expect(text('award-form-error')).toBeNull();
    });

    it('ac1_a_proxy_answer_while_loading_counts_as_unreachable', async () => {
      service.get.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));

      await open('5');

      expect(text('award-load-error')).toContain('awards.form.loadProblems.network');
    });

    it('ac1_a_refused_draft_offers_no_try_again', async () => {
      service.get.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));

      await open('5');

      expect(fixture.nativeElement.querySelector('form')).toBeNull();
      expect(text('award-load-error')).toContain('awards.form.loadProblems.denied');
      expect(text('award-load-retry')).toBeNull();
    });

    it('ac1_try_again_that_brings_the_connection_back_loads_the_draft_once', async () => {
      service.get.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })));
      await open('5');
      goOffline();
      service.get.mockReturnValue(
        of(award({ titleUk: 'Чернетка' })).pipe(tap(() => connectivity.reachable())),
      );

      component.retry();

      expect(service.get).toHaveBeenCalledTimes(2);
      expect(component.form.controls.titleUk.value).toBe('Чернетка');
    });

    it('ac1_try_again_loads_the_draft_into_the_form', async () => {
      service.get.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })));
      await open('5');
      service.get.mockReturnValue(of(award({ titleUk: 'Чернетка', version: 4 })));

      fixture.nativeElement.querySelector('[data-testid="award-load-retry"]').click();
      fixture.detectChanges();

      expect(text('award-load-error')).toBeNull();
      expect(fixture.nativeElement.querySelector('form')).not.toBeNull();
      expect(component.form.controls.titleUk.value).toBe('Чернетка');
      service.update.mockReturnValue(of(award({ version: 5 })));
      component.save();
      expect(service.update).toHaveBeenCalledWith(5, expect.anything(), 4);
    });

    it('ac1_a_draft_that_failed_to_load_is_loaded_when_the_connection_returns', async () => {
      service.get.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })));
      await open('5');
      goOffline();
      service.get.mockReturnValue(of(award({ titleUk: 'Чернетка' })));

      goOnline();

      expect(service.get).toHaveBeenCalledTimes(2);
      expect(component.form.controls.titleUk.value).toBe('Чернетка');
      expect(text('award-load-error')).toBeNull();
    });

    it('ac1_a_server_failure_while_loading_also_offers_try_again', async () => {
      service.get.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

      await open('5');

      expect(fixture.nativeElement.querySelector('form')).toBeNull();
      expect(text('award-load-error')).toContain('awards.form.loadProblems.failed');
      expect(text('award-load-retry')).toBe('awards.form.retry');
    });

    it('ac2_the_form_copy_is_offered_once_the_draft_has_loaded', async () => {
      copies.save('21', 'award-5', {
        ...emptyForm(),
        titleUk: 'Копія',
        recipientOrganizationId: null,
      });
      service.get.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })));
      await open('5');
      expect(text('restore-offer')).toBeNull();
      service.get.mockReturnValue(of(award()));

      component.retry();
      fixture.detectChanges();

      expect(text('restore-offer')).not.toBeNull();
      expect(copies.load('21', 'award-5')).toEqual(expect.objectContaining({ titleUk: 'Копія' }));
    });
  });
});

function emptyForm() {
  return {
    title: null,
    titleUk: null,
    description: null,
    descriptionUk: null,
    categoryId: null,
    awardingOrganization: null,
    awardDate: null,
    externalUrl: null,
  };
}
