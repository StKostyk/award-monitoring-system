import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { LanguageService } from '../../../core/i18n/language.service';
import { PeopleService, Person } from '../people.service';
import { PersonDialogComponent, PersonDialogData } from './person-dialog.component';

const department = {
  id: 64,
  name: 'Department of Algebra',
  nameUk: 'Кафедра алгебри',
  code: 'DAI',
  type: 'DEPARTMENT' as const,
};

const person: Person = {
  id: 41,
  firstName: 'Петро',
  lastName: 'Іваненко',
  email: 'p.ivanenko@chnu.edu.ua',
  organization: department,
  status: 'UNCLAIMED',
  addedBy: { id: 3, name: 'Секретар ФМІ', email: 'secretary.fmi@chnu.edu.ua' },
  invitedAt: '2026-11-16T10:00:00Z',
  awardCount: 0,
  version: 2,
};

const translations = {
  uk: {
    admin: {
      people: {
        unknownAddress: 'Адреса невідома',
        awardCount: 'нагород: {{count}}',
        dialog: { duplicates: 'Вже є колега з таким ім’ям', addAnyway: 'Це інша людина, додати' },
        validation: {
          required: 'Обовʼязкове поле',
          taken: 'Ця адреса вже використовується',
          institutional: 'Вкажіть університетську адресу',
          outOfScope: 'Оберіть кафедру свого факультету',
          emailKept: 'Адресу не можна видалити',
        },
        problems: { 'version-conflict': 'Хтось тим часом змінив дані' },
      },
    },
  },
};

function problem(status: number, body: object): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: body });
}

describe('PersonDialogComponent', () => {
  let fixture: ComponentFixture<PersonDialogComponent>;
  let api: Record<'add' | 'change' | 'departments', ReturnType<typeof vi.fn>>;
  let ref: { close: ReturnType<typeof vi.fn> };

  function open(data: PersonDialogData): HTMLElement {
    TestBed.overrideProvider(MAT_DIALOG_DATA, { useValue: data });
    fixture = TestBed.createComponent(PersonDialogComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function fill(first: string, last: string, email: string): void {
    fixture.componentInstance.form.setValue({
      firstName: first,
      lastName: last,
      organizationId: department.id,
      email,
    });
  }

  beforeEach(async () => {
    api = {
      add: vi.fn().mockReturnValue(of(person)),
      change: vi.fn().mockReturnValue(of(person)),
      departments: vi.fn().mockReturnValue(of([department])),
    };
    ref = { close: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [
        PersonDialogComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: translations,
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        provideRouter([]),
        { provide: PeopleService, useValue: api },
        { provide: MatDialogRef, useValue: ref },
        { provide: MAT_DIALOG_DATA, useValue: { person: null } },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
      ],
    }).compileComponents();
  });

  it('ac1_1_adds_a_person_with_trimmed_names_and_an_unknown_address_as_null', () => {
    open({ person: null });
    fill(' Оксана ', ' Мельник ', '');
    fixture.componentInstance.save();

    expect(api.add).toHaveBeenCalledWith({
      firstName: 'Оксана',
      lastName: 'Мельник',
      organizationId: department.id,
      email: null,
    });
    expect(ref.close).toHaveBeenCalledWith(person);
  });

  it('ac1_1_does_not_send_an_incomplete_form', () => {
    open({ person: null });
    fixture.componentInstance.save();

    expect(api.add).not.toHaveBeenCalled();
  });

  it('ac1_10_lists_the_matches_and_adds_again_with_the_acknowledgement', () => {
    const match = { ...person, email: 'p***@chnu.edu.ua', awardCount: 2 };
    api.add.mockReturnValueOnce(
      throwError(() =>
        problem(409, { type: 'urn:awards:problem:possible-duplicate', matches: [match] }),
      ),
    );
    const element = open({ person: null });
    fill('Петро', 'Іваненко', '');
    fixture.componentInstance.save();
    fixture.detectChanges();

    const duplicates = element.querySelector('[data-testid="person-duplicates"]');
    expect(duplicates?.textContent).toContain('Іваненко Петро');
    expect(duplicates?.textContent).toContain('p***@chnu.edu.ua');
    expect(duplicates?.textContent).toContain('нагород: 2');
    expect(ref.close).not.toHaveBeenCalled();

    (element.querySelector('[data-testid="person-add-anyway"]') as HTMLButtonElement).click();

    expect(api.add).toHaveBeenLastCalledWith(
      expect.objectContaining({ duplicateAcknowledged: true }),
    );
    expect(ref.close).toHaveBeenCalledWith(person);
  });

  it('ac1_2_marks_a_taken_address_on_its_field', () => {
    api.add.mockReturnValueOnce(
      throwError(() => problem(409, { type: 'urn:awards:problem:email-taken' })),
    );
    open({ person: null });
    fill('Петро', 'Іваненко', 'p.ivanenko@chnu.edu.ua');
    fixture.componentInstance.save();

    expect(fixture.componentInstance.form.controls.email.hasError('taken')).toBe(true);
  });

  it('ac1_3_marks_the_fields_the_server_refused', () => {
    api.add.mockReturnValueOnce(
      throwError(() =>
        problem(422, {
          type: 'urn:awards:problem:validation-failed',
          errors: [
            { field: 'organizationId', code: 'out-of-scope', message: '' },
            { field: 'email', code: 'institutional', message: '' },
          ],
        }),
      ),
    );
    open({ person: null });
    fill('Петро', 'Іваненко', 'p@gmail.com');
    fixture.componentInstance.save();

    const form = fixture.componentInstance.form;
    expect(form.controls.organizationId.hasError('out-of-scope')).toBe(true);
    expect(form.controls.email.hasError('institutional')).toBe(true);
  });

  it('ac1_6_changes_with_the_version_and_keeps_a_known_address_required', () => {
    open({ person });
    const form = fixture.componentInstance.form;

    expect(form.getRawValue().lastName).toBe('Іваненко');
    form.controls.email.setValue('');
    expect(form.controls.email.hasError('required')).toBe(true);

    form.controls.email.setValue('p.ivanenko2@chnu.edu.ua');
    fixture.componentInstance.save();

    expect(api.change).toHaveBeenCalledWith(
      person.id,
      expect.objectContaining({ email: 'p.ivanenko2@chnu.edu.ua', version: person.version }),
    );
  });

  it('ac1_6_reports_a_stale_version', () => {
    api.change.mockReturnValueOnce(
      throwError(() => problem(409, { type: 'urn:awards:problem:version-conflict' })),
    );
    const element = open({ person });
    fixture.componentInstance.save();
    fixture.detectChanges();

    expect(element.querySelector('[data-testid="person-dialog-error"]')?.textContent).toContain(
      'Хтось тим часом змінив дані',
    );
  });
});
