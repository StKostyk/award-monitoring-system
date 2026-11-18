import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent } from '@angular/material/card';
import { MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatOption, MatSelect } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { knownProblem, problemType } from '../../../core/api/problem';
import { LanguageService } from '../../../core/i18n/language.service';
import { attachmentName, saveFile } from '../../../shared/file-download';
import { IconComponent } from '../../../shared/icons/icon.component';
import { KyivDatePipe } from '../../../shared/kyiv-date.pipe';
import { organizationName } from '../../../shared/organization-name';
import {
  RECOGNITION_LEVELS,
  RecognitionLevel,
  UnitRef,
  categoryName,
} from '../../awards/awards.service';
import {
  AwardImportPreview,
  AwardImportResult,
  AwardImportService,
  IMPORT_FIELDS,
  ImportField,
  ImportMapping,
  ImportProblem,
  ImportRow,
} from '../award-import.service';
import { PeopleService } from '../people.service';

/** Problems the page explains in its own words; anything else shows the generic message. */
const KNOWN = [
  'file-too-large',
  'unsupported-type',
  'file-empty',
  'too-many-rows',
  'file-unreadable',
  'malware-detected',
  'scanner-unavailable',
  'validation-failed',
  'preview-stale',
  'rows-blocked',
  'access-denied',
  'network',
];

/** The spreadsheet types the picker offers; the server checks the content. */
const ACCEPT =
  '.xlsx,.csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,text/csv';

/** The longest default awarding organisation the server takes. */
const ORGANIZATION_LENGTH = 255;

/** Imports colleagues and their past awards from a spreadsheet: pick, map the columns, preview, import (2.5.5). */
@Component({
  selector: 'app-award-import',
  imports: [
    IconComponent,
    KyivDatePipe,
    MatButton,
    MatCard,
    MatCardContent,
    MatFormField,
    MatHint,
    MatInput,
    MatLabel,
    MatOption,
    MatProgressBar,
    MatSelect,
    RouterLink,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './award-import.component.html',
  styleUrl: './award-import.component.scss',
})
export class AwardImportComponent {
  private readonly api = inject(AwardImportService);
  private readonly people = inject(PeopleService);
  private readonly language = inject(LanguageService);

  protected readonly accept = ACCEPT;
  protected readonly fields = IMPORT_FIELDS;
  protected readonly levels = RECOGNITION_LEVELS;
  protected readonly organizationLength = ORGANIZATION_LENGTH;

  protected readonly file = signal<File | null>(null);
  protected readonly preview = signal<AwardImportPreview | null>(null);
  /** The field each column fills, by 0-based column; null leaves the column unread. */
  protected readonly assignments = signal<(ImportField | null)[]>([]);
  protected readonly departmentId = signal<number | null>(null);
  protected readonly defaultLevel = signal<RecognitionLevel | null>(null);
  protected readonly defaultOrganization = signal('');
  /** The mapping differs from the one the shown preview used. */
  protected readonly changed = signal(false);
  protected readonly departments = signal<UnitRef[]>([]);
  protected readonly busy = signal(false);
  protected readonly problem = signal<string | null>(null);
  protected readonly blockedRows = signal<number[]>([]);
  protected readonly result = signal<AwardImportResult | null>(null);

  protected readonly rows = computed(() => this.preview()?.rows ?? []);
  protected readonly blocked = computed(() => this.rows().filter(blocking).length);
  protected readonly skipped = computed(
    () => this.rows().filter((row) => row.skipped && !blocking(row)).length,
  );
  protected readonly ready = computed(() => this.rows().length - this.blocked() - this.skipped());
  protected readonly peopleAdded = computed(() => {
    const names = this.rows()
      .filter((row) => !row.skipped && !blocking(row) && row.person && row.person.id === null)
      .map((row) =>
        `${row.department?.id}|${row.person?.lastName}|${row.person?.firstName}`.toLowerCase(),
      );
    return new Set(names).size;
  });
  protected readonly canImport = computed(
    () =>
      !this.busy() &&
      !this.changed() &&
      this.preview() !== null &&
      this.blocked() === 0 &&
      this.ready() > 0,
  );

  constructor() {
    this.people.departments().subscribe({
      next: (units) => this.departments.set(units),
      error: () => this.departments.set([]),
    });
  }

  protected picked(input: HTMLInputElement): void {
    const file = input.files?.item(0) ?? null;
    input.value = '';
    if (!file) {
      return;
    }
    this.file.set(file);
    this.preview.set(null);
    this.result.set(null);
    this.load(null);
  }

  protected assign(column: number, field: ImportField | null): void {
    this.assignments.update((current) =>
      current.map((value, index) => {
        if (index === column) {
          return field;
        }
        return field !== null && value === field ? null : value;
      }),
    );
    this.changed.set(true);
  }

  protected setDepartment(id: number | null): void {
    this.departmentId.set(id);
    this.changed.set(true);
  }

  protected setLevel(level: RecognitionLevel | null): void {
    this.defaultLevel.set(level);
    this.changed.set(true);
  }

  protected setOrganization(value: string): void {
    this.defaultOrganization.set(value);
    this.changed.set(true);
  }

  protected refresh(): void {
    this.load(this.mapping());
  }

  protected importRows(): void {
    const file = this.file();
    const preview = this.preview();
    if (!file || !preview || !this.canImport()) {
      return;
    }
    this.busy.set(true);
    this.problem.set(null);
    this.api.importRows(file, preview.mapping, preview.checksum).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.result.set(result);
        this.preview.set(null);
        this.file.set(null);
      },
      error: (err: unknown) => this.refused(err),
    });
  }

  protected startOver(): void {
    this.file.set(null);
    this.preview.set(null);
    this.result.set(null);
    this.problem.set(null);
    this.blockedRows.set([]);
  }

  protected downloadTemplate(): void {
    this.api.template().subscribe({
      next: (response) =>
        saveFile(
          response.body ?? new Blob(),
          attachmentName(response.headers.get('Content-Disposition'), 'awards-import.xlsx'),
        ),
      error: () => this.problem.set('admin.import.problems.template'),
    });
  }

  protected isBlocking(row: ImportRow): boolean {
    return blocking(row);
  }

  /** The problems of a row as chips, without the added-person notice the «will be added» chip already shows; null without any. */
  protected shownProblems(row: ImportRow): ImportProblem[] | null {
    const problems = row.problems.filter((problem) => problem.code !== 'person-added');
    return problems.length ? problems : null;
  }

  protected unitName(unit: UnitRef): string {
    return organizationName(unit, this.language.current());
  }

  protected categoryOf(row: ImportRow): string {
    return row.category ? categoryName(row.category, this.language.current()) : '';
  }

  private load(mapping: ImportMapping | null): void {
    const file = this.file();
    if (!file) {
      return;
    }
    this.busy.set(true);
    this.problem.set(null);
    this.blockedRows.set([]);
    this.api.preview(file, mapping).subscribe({
      next: (preview) => this.shown(preview),
      error: (err: unknown) => this.refused(err),
    });
  }

  private shown(preview: AwardImportPreview): void {
    this.busy.set(false);
    this.preview.set(preview);
    this.assignments.set(
      preview.headers.map((_, column) => {
        const entry = Object.entries(preview.mapping.columns).find(([, index]) => index === column);
        return entry ? (entry[0] as ImportField) : null;
      }),
    );
    this.departmentId.set(preview.mapping.departmentId);
    this.defaultLevel.set(preview.mapping.defaultLevel);
    this.defaultOrganization.set(preview.mapping.defaultAwardingOrganization ?? '');
    this.changed.set(false);
  }

  private mapping(): ImportMapping {
    const columns: Partial<Record<ImportField, number>> = {};
    this.assignments().forEach((field, column) => {
      if (field) {
        columns[field] = column;
      }
    });
    const organization = this.defaultOrganization().trim();
    return {
      columns,
      departmentId: this.departmentId(),
      defaultLevel: this.defaultLevel(),
      defaultAwardingOrganization: organization === '' ? null : organization,
    };
  }

  private refused(err: unknown): void {
    this.busy.set(false);
    const type = knownProblem(problemType(err), KNOWN);
    if (type === 'rows-blocked' && err instanceof HttpErrorResponse) {
      this.blockedRows.set((err.error as { rows?: number[] } | null)?.rows ?? []);
    }
    this.problem.set(`admin.import.problems.${type}`);
  }
}

function blocking(row: ImportRow): boolean {
  return row.problems.some((problem) => problem.blocking);
}
