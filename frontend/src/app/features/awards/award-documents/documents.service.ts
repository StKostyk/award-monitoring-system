import { HttpClient, HttpEvent } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';

export type DocumentType = 'CERTIFICATE' | 'DIPLOMA' | 'SUPPORTING_DOCUMENT' | 'PHOTO';

export const DOCUMENT_TYPES: DocumentType[] = [
  'CERTIFICATE',
  'DIPLOMA',
  'SUPPORTING_DOCUMENT',
  'PHOTO',
];

const KILOBYTE = 1024;
const MEGABYTE = KILOBYTE * KILOBYTE;

/** Largest file the server accepts, in bytes. */
export const MAX_DOCUMENT_SIZE = 10 * 1024 * 1024;
/** Most documents one award can have. */
export const MAX_DOCUMENTS = 10;
/** Media types the server accepts, with the extensions that belong to them. */
export const ACCEPTED_FORMATS: Record<string, string[]> = {
  'application/pdf': ['pdf'],
  'image/jpeg': ['jpg', 'jpeg'],
  'image/png': ['png'],
  'image/webp': ['webp'],
};

/** A file attached to an award, without its content. */
export interface AwardDocument {
  id: number;
  awardId: number;
  fileName: string;
  type: DocumentType;
  mimeType: string;
  size: number;
  description: string | null;
  uploadedAt: string;
  uploadedBy: { id: number; name: string };
  /** The owner shows it to signed-in colleagues while the award is shared. */
  shownToColleagues: boolean;
}

@Injectable({ providedIn: 'root' })
export class DocumentsService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /**
   * The documents of an award, oldest first.
   *
   * @param awardId the award
   * @returns the documents
   */
  list(awardId: number): Observable<AwardDocument[]> {
    return this.http.get<AwardDocument[]>(`${this.base}/awards/${awardId}/documents`);
  }

  /**
   * Uploads a file to a draft, reporting the upload progress.
   *
   * @param awardId the draft
   * @param file the file
   * @param type what the document is
   * @returns the upload events, ending with the stored document
   */
  upload(awardId: number, file: File, type: DocumentType): Observable<HttpEvent<AwardDocument>> {
    const body = new FormData();
    body.append('file', file, file.name);
    body.append('type', type);
    return this.http.post<AwardDocument>(`${this.base}/awards/${awardId}/documents`, body, {
      observe: 'events',
      reportProgress: true,
    });
  }

  /**
   * The content of a document, fetched with the access token.
   *
   * @param id the document
   * @returns the content
   */
  download(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/documents/${id}`, { responseType: 'blob' });
  }

  /**
   * Shows a document of an own shared award to colleagues, or hides it again.
   *
   * @param id the document
   * @param shown whether colleagues see it
   * @returns the document with its choice
   */
  share(id: number, shown: boolean): Observable<AwardDocument> {
    return this.http.put<AwardDocument>(`${this.base}/documents/${id}/sharing`, {
      shownToColleagues: shown,
    });
  }

  /**
   * Removes a document from a draft.
   *
   * @param id the document
   * @returns completes when removed
   */
  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/documents/${id}`);
  }
}

/**
 * A file size in kilobytes below a megabyte, in megabytes from there on.
 *
 * @param bytes the size
 * @param language the interface language
 * @returns the number in the language's format
 */
export function sizeValue(bytes: number, language: string): string {
  const format = new Intl.NumberFormat(language === 'en' ? 'en-GB' : 'uk-UA', {
    maximumFractionDigits: 1,
  });
  return bytes < MEGABYTE
    ? format.format(Math.max(1, Math.round(bytes / KILOBYTE)))
    : format.format(bytes / MEGABYTE);
}

/** Translation key of the unit of `sizeValue`. */
export function sizeUnit(bytes: number): string {
  return bytes < MEGABYTE ? 'awards.documents.kilobytes' : 'awards.documents.megabytes';
}

/** The extension of a file name in lower case, empty when it has none. */
export function extensionOf(name: string): string {
  const dot = name.lastIndexOf('.');
  return dot < 0 ? '' : name.substring(dot + 1).toLowerCase();
}

/** Whether the browser's media type or the file's extension is one the server accepts. */
export function isAcceptedFile(file: File): boolean {
  return (
    file.type in ACCEPTED_FORMATS ||
    Object.values(ACCEPTED_FORMATS).some((extensions) =>
      extensions.includes(extensionOf(file.name)),
    )
  );
}
