import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { FormCopiesService } from '../../../core/storage/form-copies.service';
import { Award, AwardsService, CategoryNode, CategorySuggestion } from '../awards.service';
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
    organization: { id: 64, name: 'Algebra', nameUk: 'Кафедра алгебри', code: 'DAI', type: 'DEPARTMENT' },
    request: null,
    warnings: [],
    createdAt: '2026-09-28T08:00:00Z',
    updatedAt: '2026-09-28T08:00:00Z',
    version: 1,
    ...overrides,
  };
}

function problem(type: string, status: number, errors: unknown[] = []): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: { type: `urn:awards:problem:${type}`, errors } });
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
  };
  const dialog = { open: vi.fn() };
  const auth = { userId: signal<string | null>('21'), isAuthenticated: signal(true) };

  async function open(id: string | null): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [
        AwardFormComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: { uk: {} },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(id ? { id } : {}) } } },
        { provide: AwardsService, useValue: service },
        { provide: MatDialog, useValue: dialog },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
        { provide: AuthService, useValue: auth },
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
    dialog.open.mockReset();
    auth.userId.set('21');
    auth.isAuthenticated.set(true);
  });

  it('ac1_1_saves_a_draft_with_only_a_title_and_moves_to_its_address', async () => {
    await open(null);
    service.create.mockReturnValue(of(award()));
    type('titleUk', '  Грамота  ');

    component.save();

    expect(service.create).toHaveBeenCalledWith(expect.objectContaining({ titleUk: 'Грамота', title: null }));
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

  it('ac1_5_ac1_9_saves_then_submits_and_shows_the_confirmation', async () => {
    await open(null);
    service.create.mockReturnValue(of(award({ version: 2 })));
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
    const input = fixture.nativeElement.querySelector('[data-testid="award-date"]') as HTMLInputElement;

    expect(input.max).toBe(component.today);
    expect(input.min).toBe(`${Number(component.today.substring(0, 4)) - 50}${component.today.substring(4)}`);
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
    const match = { id: 9, title: null, titleUk: 'Грамота МОН', awardDate: '2025-05-01', status: 'PENDING' as const };
    service.get.mockReturnValue(
      of(award({ warnings: [{ code: 'POSSIBLE_DUPLICATE', field: 'title', matches: [match] }] })),
    );
    await open('5');

    const link = fixture.nativeElement.querySelector('[data-testid="award-duplicate-9"]') as HTMLAnchorElement;
    expect(link.textContent).toContain('Грамота МОН');
    expect(link.getAttribute('href')).toBe('/awards/9');

    type('awardDate', '2024-01-01');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="award-duplicate-warning"]')).toBeNull();
  });

  it('ac2_5_a_possible_duplicate_is_submitted_after_the_owner_confirms_it', async () => {
    const match = { id: 9, title: null, titleUk: 'Грамота МОН', awardDate: '2025-05-01', status: 'PENDING' };
    await open(null);
    service.create.mockReturnValue(of(award({ version: 2 })));
    service.submit
      .mockReturnValueOnce(throwError(() => duplicateOf([match])))
      .mockReturnValueOnce(of(award({ status: 'PENDING', version: 3 })));
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    fillComplete();

    component.submit();

    expect(dialog.open).toHaveBeenCalledWith(expect.anything(), expect.objectContaining({ data: { matches: [match] } }));
    expect(service.submit).toHaveBeenLastCalledWith(5, 2, true);
    expect(router.navigate).toHaveBeenCalledWith(['/awards', 5, 'submitted'], { replaceUrl: true });
  });

  it('ac2_5_cancelling_the_duplicate_dialog_leaves_a_saved_draft', async () => {
    await open(null);
    service.create.mockReturnValue(of(award({ version: 2 })));
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

    expect(service.update).toHaveBeenCalledWith(5, expect.objectContaining({ titleUk: 'Моя версія' }), 1);
    expect(component.stale()).toBe(true);
    expect(fixture.nativeElement.querySelector('[data-testid="award-reload"]')).not.toBeNull();
    expect(copies.load('21', 'award-5')).toEqual(expect.objectContaining({ titleUk: 'Моя версія' }));

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

    expect(router.navigate).toHaveBeenCalledWith(['/awards', 5], { state: { problem: 'award-not-editable' } });
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

    expect(component.problem()).toBe('awards.problems.network');
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

    expect(copies.load('21', 'award-new')).toEqual(expect.objectContaining({ titleUk: 'Половина' }));
    expect(event.defaultPrevented).toBe(true);
  });

  it('f4_an_ended_session_keeps_the_copy_for_its_user_without_holding_the_page', async () => {
    await open(null);
    type('titleUk', 'Половина');
    auth.userId.set(null);
    auth.isAuthenticated.set(false);
    const event = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent;

    component.keepOnUnload(event);

    expect(copies.load('21', 'award-new')).toEqual(expect.objectContaining({ titleUk: 'Половина' }));
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
    expect(copies.load('21', 'award-new')).toEqual(expect.objectContaining({ titleUk: 'Змінена грамота' }));

    service.create.mockReturnValue(of(award({ id: 6, titleUk: 'Змінена грамота' })));
    component.save();

    expect(service.create).toHaveBeenCalledWith(expect.objectContaining({ titleUk: 'Змінена грамота' }));
    expect(location.replaceState).toHaveBeenCalledWith('/awards/6/edit');
  });

  it('f9_a_draft_is_deleted_after_confirmation_and_the_list_is_shown', async () => {
    service.get.mockReturnValue(of(award()));
    await open('5');
    fixture.detectChanges();
    copies.save('21', 'award-5', emptyForm());
    service.remove.mockReturnValue(of(undefined));

    dialog.open.mockReturnValue({ afterClosed: () => of(false) });
    (fixture.nativeElement.querySelector('[data-testid="award-remove"]') as HTMLButtonElement).click();
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

    expect(router.navigate).toHaveBeenCalledWith(['/awards'], expect.objectContaining({ replaceUrl: true }));
  });

  it('f9_a_submitted_draft_is_not_deleted_and_opens_read_only', async () => {
    service.get.mockReturnValue(of(award()));
    await open('5');
    service.remove.mockReturnValue(throwError(() => problem('award-not-editable', 409)));
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });

    component.remove();

    expect(router.navigate).toHaveBeenCalledWith(['/awards', 5], { state: { problem: 'award-not-editable' } });
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
      return Array.from(fixture.nativeElement.querySelectorAll('[data-testid^="category-suggestion-"]'));
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
      service.suggestions.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
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

      const [title, organization] = service.suggestions.mock.calls[0] as unknown as [string, string];
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
