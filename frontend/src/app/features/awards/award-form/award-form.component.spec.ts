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
import { Award, AwardsService, CategoryNode } from '../awards.service';
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
  };
  const dialog = { open: vi.fn() };

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
        { provide: AuthService, useValue: { userId: signal('21') } },
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

  beforeEach(() => {
    localStorage.clear();
    Object.values(service).forEach((mock) => mock.mockClear());
    service.categories.mockImplementation(() => of(tree));
    dialog.open.mockReset();
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
