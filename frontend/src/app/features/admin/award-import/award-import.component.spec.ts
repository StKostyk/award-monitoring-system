import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { languageStub, translations } from '../../analytics/analytics.testing';
import { AwardImportPreview, AwardImportService, ImportRow } from '../award-import.service';
import { PeopleService } from '../people.service';
import { AwardImportComponent } from './award-import.component';

const algebra = {
  id: 41,
  name: 'Department of Algebra and Informatics',
  nameUk: 'Кафедра алгебри та інформатики',
  code: 'AI',
  type: 'DEPARTMENT' as const,
};

function row(overrides: Partial<ImportRow> = {}): ImportRow {
  return {
    row: 2,
    person: { id: null, firstName: 'Олена', lastName: 'Коваль', withoutAccount: true },
    department: algebra,
    title: 'Подяка ректора',
    awardDate: '2019-05-01',
    awardingOrganization: 'ЧНУ',
    notes: null,
    category: null,
    skipped: false,
    problems: [{ code: 'person-added', field: 'FULL_NAME', blocking: false }],
    ...overrides,
  };
}

function preview(rows: ImportRow[], checksum = 'abc'): AwardImportPreview {
  return {
    checksum,
    headers: ['ПІБ', 'Назва', 'Дата'],
    mapping: {
      columns: { FULL_NAME: 0, TITLE: 1, DATE: 2 },
      departmentId: 41,
      defaultLevel: 'UNIVERSITY',
      defaultAwardingOrganization: 'ЧНУ',
    },
    rows,
  };
}

describe('AwardImportComponent', () => {
  let fixture: ComponentFixture<AwardImportComponent>;
  let api: {
    preview: ReturnType<typeof vi.fn>;
    importRows: ReturnType<typeof vi.fn>;
    template: ReturnType<typeof vi.fn>;
  };
  const file = new File(['ПІБ;Назва;Дата'], 'awards.csv', { type: 'text/csv' });

  beforeEach(async () => {
    api = { preview: vi.fn(), importRows: vi.fn(), template: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [AwardImportComponent, NoopAnimationsModule, translations('uk')],
      providers: [
        provideRouter([]),
        { provide: AwardImportService, useValue: api },
        { provide: PeopleService, useValue: { departments: () => of([algebra]) } },
        languageStub('uk').provider,
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AwardImportComponent);
    fixture.detectChanges();
  });

  afterEach(() => vi.restoreAllMocks());

  function query(id: string): HTMLElement {
    return fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
  }

  function pick(): void {
    const input = query('import-input') as HTMLInputElement;
    Object.defineProperty(input, 'files', { value: { item: () => file }, configurable: true });
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  it('ac5_1_previews_the_picked_file_with_the_guessed_columns_and_marks_people_to_add', () => {
    api.preview.mockReturnValue(of(preview([row()])));

    pick();

    expect(api.preview).toHaveBeenCalledWith(file, null);
    expect(query('import-file-name').textContent).toContain('awards.csv');
    expect(query('import-summary').textContent).toContain('До імпорту: 1');
    expect(query('import-summary').textContent).toContain('Нових колег: 1');
    expect(query('import-row-2').textContent).toContain('Коваль Олена');
    expect(query('import-row-2').textContent).toContain('Буде додано');
    expect(query('import-row-2').textContent).not.toContain('Колегу буде додано');
    expect(query('import-row-2').textContent).toContain('Кафедра алгебри та інформатики');
    expect((query('import-submit') as HTMLButtonElement).disabled).toBe(false);
  });

  it('ac5_1_sends_the_changed_mapping_and_holds_the_import_until_it_is_reviewed', () => {
    api.preview.mockReturnValue(of(preview([row()])));
    pick();
    const component = fixture.componentInstance as unknown as {
      assign(column: number, field: string | null): void;
      setOrganization(value: string): void;
    };

    component.assign(2, 'TITLE');
    component.setOrganization('  ');
    fixture.detectChanges();

    expect(query('import-changed')).not.toBeNull();
    expect((query('import-submit') as HTMLButtonElement).disabled).toBe(true);
    query('import-refresh').click();

    expect(api.preview).toHaveBeenLastCalledWith(file, {
      columns: { FULL_NAME: 0, TITLE: 2 },
      departmentId: 41,
      defaultLevel: 'UNIVERSITY',
      defaultAwardingOrganization: null,
    });
  });

  it('ac5_1_keeps_the_import_disabled_while_a_row_has_a_blocking_problem', () => {
    api.preview.mockReturnValue(
      of(
        preview([
          row(),
          row({
            row: 3,
            person: null,
            problems: [{ code: 'name-missing', field: 'FULL_NAME', blocking: true }],
          }),
          row({
            row: 4,
            skipped: true,
            problems: [{ code: 'already-stored', field: null, blocking: false }],
          }),
        ]),
      ),
    );

    pick();

    expect(query('import-summary').textContent).toContain('З помилками: 1');
    expect(query('import-summary').textContent).toContain('Пропущено: 1');
    expect(query('import-row-3').classList).toContain('import__row--blocked');
    expect(query('import-row-3').textContent).toContain('Немає імені');
    expect(query('import-row-4').classList).toContain('import__row--skipped');
    expect((query('import-submit') as HTMLButtonElement).disabled).toBe(true);
  });

  it('ac5_2_imports_the_previewed_rows_and_shows_the_result', () => {
    api.preview.mockReturnValue(of(preview([row()], 'sum')));
    const answers = new Subject<{ imported: number; peopleAdded: number; skipped: number }>();
    api.importRows.mockReturnValue(answers);
    pick();

    query('import-submit').click();
    fixture.detectChanges();

    expect(api.importRows).toHaveBeenCalledWith(file, preview([]).mapping, 'sum');
    answers.next({ imported: 1, peopleAdded: 1, skipped: 0 });
    fixture.detectChanges();
    expect(query('import-result-imported').textContent).toContain('Імпортовано нагород: 1');
    expect(query('import-result-people').textContent).toContain('1');
    expect(query('import-submit')).toBeNull();

    query('import-again').click();
    fixture.detectChanges();
    expect(query('import-result')).toBeNull();
    expect(query('import-file-name')).toBeNull();
  });

  it('ac5_4_explains_a_refused_file', () => {
    api.preview.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 422,
            error: { type: 'urn:awards:problem:unsupported-type' },
          }),
      ),
    );

    pick();

    expect(query('import-problem').textContent).toContain('Підходять лише файли .xlsx і .csv.');
    expect(query('import-summary')).toBeNull();
  });

  it('ac5_4_names_the_blocked_rows_the_server_refused', () => {
    api.preview.mockReturnValue(of(preview([row()])));
    api.importRows.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 422,
            error: { type: 'urn:awards:problem:rows-blocked', rows: [2, 5] },
          }),
      ),
    );
    pick();

    query('import-submit').click();
    fixture.detectChanges();

    expect(query('import-problem').textContent).toContain('Деякі рядки мають помилки.');
    expect(query('import-problem').textContent).toContain('Рядки: 2, 5.');
  });

  it('ac5_1_downloads_the_template_under_the_server_name', () => {
    const saved: string[] = [];
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:template');
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      saved.push(this.download);
    });
    api.template.mockReturnValue(
      of(
        new HttpResponse({
          body: new Blob(['x']),
          headers: new HttpHeaders({ 'Content-Disposition': 'attachment; filename="import.xlsx"' }),
        }),
      ),
    );

    query('import-template').click();

    expect(saved).toEqual(['import.xlsx']);
  });
});
