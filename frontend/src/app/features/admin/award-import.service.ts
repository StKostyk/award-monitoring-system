import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CategoryRef, RecognitionLevel, UnitRef } from '../awards/awards.service';

/** The fields a spreadsheet column may fill. */
export type ImportField =
  | 'FULL_NAME'
  | 'LAST_NAME'
  | 'FIRST_NAME'
  | 'EMAIL'
  | 'DEPARTMENT'
  | 'TITLE'
  | 'DATE'
  | 'LEVEL'
  | 'AWARDING_ORGANIZATION'
  | 'NOTES';

/** The fields in the order the mapping editor offers them. */
export const IMPORT_FIELDS: ImportField[] = [
  'FULL_NAME',
  'LAST_NAME',
  'FIRST_NAME',
  'EMAIL',
  'DEPARTMENT',
  'TITLE',
  'DATE',
  'LEVEL',
  'AWARDING_ORGANIZATION',
  'NOTES',
];

/** Which 0-based column fills which field, and the values of rows that leave a field empty. */
export interface ImportMapping {
  columns: Partial<Record<ImportField, number>>;
  departmentId: number | null;
  defaultLevel: RecognitionLevel | null;
  defaultAwardingOrganization: string | null;
}

export interface ImportProblem {
  code: string;
  field: ImportField | null;
  blocking: boolean;
}

/** One row of the spreadsheet as the import reads it. */
export interface ImportRow {
  row: number;
  /** The matched member (`id` set) or the person the import adds (`id` null); null when the name is unreadable. */
  person: {
    id: number | null;
    firstName: string;
    lastName: string;
    withoutAccount: boolean;
  } | null;
  department: UnitRef | null;
  title: string | null;
  awardDate: string | null;
  awardingOrganization: string | null;
  notes: string | null;
  category: CategoryRef | null;
  skipped: boolean;
  problems: ImportProblem[];
}

export interface AwardImportPreview {
  checksum: string;
  headers: string[];
  mapping: ImportMapping;
  rows: ImportRow[];
}

export interface AwardImportResult {
  imported: number;
  peopleAdded: number;
  skipped: number;
}

@Injectable({ providedIn: 'root' })
export class AwardImportService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/award-imports`;

  /**
   * Reads the file and answers every row; stores nothing.
   *
   * @param file the spreadsheet
   * @param mapping the columns to read; null lets the server guess them from the headers
   */
  preview(file: File, mapping: ImportMapping | null): Observable<AwardImportPreview> {
    const body = new FormData();
    body.append('file', file);
    if (mapping) {
      body.append('mapping', json(mapping));
    }
    return this.http.post<AwardImportPreview>(`${this.base}/preview`, body);
  }

  /**
   * Stores the rows of an unchanged preview.
   *
   * @param file the previewed spreadsheet
   * @param mapping the previewed mapping
   * @param checksum the checksum of that preview
   */
  importRows(file: File, mapping: ImportMapping, checksum: string): Observable<AwardImportResult> {
    const body = new FormData();
    body.append('file', file);
    body.append('mapping', json(mapping));
    body.append('checksum', checksum);
    return this.http.post<AwardImportResult>(this.base, body);
  }

  /** The empty workbook with the columns the import recognises. */
  template(): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.base}/template`, { observe: 'response', responseType: 'blob' });
  }
}

function json(mapping: ImportMapping): Blob {
  return new Blob([JSON.stringify(mapping)], { type: 'application/json' });
}
