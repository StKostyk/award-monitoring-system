import { HttpErrorResponse, HttpEvent, HttpEventType, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Observable, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { LanguageService } from '../../../core/i18n/language.service';
import { AwardDocumentsComponent } from './award-documents.component';
import { DocumentPreviewDialogComponent } from './document-preview-dialog.component';
import { AwardDocument, DocumentsService, MAX_DOCUMENT_SIZE } from './documents.service';

function document(overrides: Partial<AwardDocument> = {}): AwardDocument {
  return {
    id: 1,
    awardId: 5,
    fileName: 'диплом.pdf',
    type: 'CERTIFICATE',
    mimeType: 'application/pdf',
    size: 2 * 1024 * 1024,
    description: null,
    uploadedAt: '2026-10-01T22:30:00Z',
    uploadedBy: { id: 21, name: 'Анастасія Коваль' },
    ...overrides,
  };
}

function file(name: string, size = 1000, type = ''): File {
  const content = new File(['x'], name, { type });
  Object.defineProperty(content, 'size', { value: size });
  return content;
}

function problem(type: string, status: number): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: { type: `urn:awards:problem:${type}` } });
}

function stored(body: AwardDocument): Observable<HttpEvent<AwardDocument>> {
  const progress: HttpEvent<AwardDocument> = {
    type: HttpEventType.UploadProgress,
    loaded: 50,
    total: 100,
  };
  return of(progress, new HttpResponse({ status: 201, body }));
}

describe('AwardDocumentsComponent', () => {
  let fixture: ComponentFixture<AwardDocumentsComponent>;
  let component: AwardDocumentsComponent;
  const service = {
    list: vi.fn((): Observable<AwardDocument[]> => of([])),
    upload: vi.fn(),
    download: vi.fn(),
    remove: vi.fn(),
  };
  const dialog = { open: vi.fn() };
  const language = { current: vi.fn(() => 'uk') };

  async function render(inputs: Record<string, unknown>): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [
        AwardDocumentsComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: { uk: {} },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        { provide: DocumentsService, useValue: service },
        { provide: MatDialog, useValue: dialog },
        { provide: LanguageService, useValue: language },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AwardDocumentsComponent);
    component = fixture.componentInstance;
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    fixture.detectChanges();
  }

  function byTestId(id: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${id}"]`);
  }

  function all(id: string): HTMLElement[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll(`[data-testid="${id}"]`),
    );
  }

  beforeEach(() => {
    Object.values(service).forEach((mock) => mock.mockReset());
    service.list.mockReturnValue(of([]));
    dialog.open.mockReset();
    language.current.mockReturnValue('uk');
  });

  it('ac1_form_section_shows_drop_zone_type_and_hint', async () => {
    await render({ awardId: 5, uploads: true, removable: true });

    expect(byTestId('documents-drop')?.textContent).toContain('awards.documents.drop');
    expect(byTestId('documents-drop')?.getAttribute('tabindex')).toBe('0');
    expect(byTestId('documents-input')?.getAttribute('accept')).toContain('.webp');
    expect(byTestId('documents-camera-input')?.getAttribute('capture')).toBe('environment');
    expect(component.nextType()).toBe('CERTIFICATE');
    await fixture.whenStable();
    fixture.detectChanges();
    expect(byTestId('documents-type')?.textContent).toContain('awards.documents.types.CERTIFICATE');
    expect(fixture.nativeElement.textContent).toContain('awards.documents.hint');
  });

  it('ac1_type_defaults_to_supporting_document_once_a_file_exists', async () => {
    service.list.mockReturnValue(of([document()]));
    await render({ awardId: 5, uploads: true });

    expect(component.nextType()).toBe('SUPPORTING_DOCUMENT');
  });

  it('ac1_first_file_is_a_certificate_and_the_rest_supporting_documents', async () => {
    service.upload.mockReturnValue(new Subject());
    await render({ awardId: 5, uploads: true });

    component.add([file('a.pdf'), file('b.jpg')]);

    expect(component.queue().map((item) => item.type)).toEqual([
      'CERTIFICATE',
      'SUPPORTING_DOCUMENT',
    ]);
  });

  it('ac1_a_chosen_type_applies_to_the_next_files_only', async () => {
    service.upload.mockReturnValue(new Subject());
    await render({ awardId: 5, uploads: true });

    component.chooseType('DIPLOMA');
    component.add([file('a.pdf'), file('b.pdf')]);
    component.add([file('c.pdf')]);

    expect(component.queue().map((item) => item.type)).toEqual([
      'DIPLOMA',
      'DIPLOMA',
      'SUPPORTING_DOCUMENT',
    ]);
  });

  it('ac2_new_award_is_saved_before_the_first_upload', async () => {
    const prepare = vi.fn(() => of(42));
    service.upload.mockReturnValue(stored(document({ id: 7, awardId: 42 })));
    await render({ awardId: null, uploads: true, prepare });

    component.add([file('a.pdf'), file('b.png')]);

    expect(prepare).toHaveBeenCalledTimes(1);
    expect(service.upload).toHaveBeenCalledTimes(2);
    expect(service.upload.mock.calls[0][0]).toBe(42);
    fixture.componentRef.setInput('awardId', 42);
    fixture.detectChanges();
    expect(service.list).not.toHaveBeenCalled();
  });

  it('ac2_file_stays_queued_when_the_save_fails', async () => {
    const prepare = vi.fn(() => throwError(() => new Error('invalid')));
    await render({ awardId: null, uploads: true, prepare });

    component.add([file('a.pdf')]);

    expect(service.upload).not.toHaveBeenCalled();
    expect(component.queue()[0]).toMatchObject({
      state: 'failed',
      problem: 'awards.documents.problems.not-saved',
    });
  });

  it('ac3_files_the_server_would_refuse_are_never_sent', async () => {
    service.list.mockReturnValue(of(Array.from({ length: 9 }, (_, id) => document({ id }))));
    service.upload.mockReturnValue(new Subject());
    await render({ awardId: 5, uploads: true });

    component.add([
      file(
        'scan.docx',
        1000,
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      ),
      file('IMG_2041.HEIC', 1000, 'image/heic'),
      file('big.pdf', MAX_DOCUMENT_SIZE + 1, 'application/pdf'),
      file('empty.pdf', 0, 'application/pdf'),
      file('ninth.png', 1000, 'image/png'),
      file('eleventh.png', 1000, 'image/png'),
    ]);
    fixture.detectChanges();

    expect(component.queue().map((item) => item.problem)).toEqual([
      'awards.documents.problems.unsupported-type',
      'awards.documents.problems.unsupported-type',
      'awards.documents.problems.file-too-large',
      'awards.documents.problems.empty-file',
      null,
      'awards.documents.problems.document-limit',
    ]);
    expect(service.upload).toHaveBeenCalledTimes(1);
    expect(all('documents-retry')).toHaveLength(0);
    expect(all('documents-dismiss')).toHaveLength(5);
  });

  it('ac4_files_upload_one_after_another_with_progress_and_announcement', async () => {
    const first = new Subject<HttpEvent<AwardDocument>>();
    service.upload
      .mockReturnValueOnce(first)
      .mockReturnValueOnce(stored(document({ id: 9, fileName: 'b.png' })));
    await render({ awardId: 5, uploads: true });

    component.add([file('a.pdf'), file('b.png')]);
    first.next({ type: HttpEventType.UploadProgress, loaded: 30, total: 100 });
    fixture.detectChanges();

    expect(service.upload).toHaveBeenCalledTimes(1);
    expect(component.queue()[0]).toMatchObject({ state: 'uploading', progress: 30 });
    expect(byTestId('documents-progress')).not.toBeNull();

    first.next(new HttpResponse({ status: 201, body: document({ id: 8, fileName: 'a.pdf' }) }));
    first.complete();
    fixture.detectChanges();

    expect(service.upload).toHaveBeenCalledTimes(2);
    expect(component.documents().map((item) => item.id)).toEqual([8, 9]);
    expect(component.queue()).toHaveLength(0);
    expect(byTestId('documents-announcement')?.getAttribute('aria-live')).toBe('polite');
    expect(component.announcement()).toBe('awards.documents.uploaded');
  });

  it('ac5_refusals_a_retry_cannot_fix_offer_only_dismiss', async () => {
    service.upload.mockReturnValue(throwError(() => problem('duplicate-document', 409)));
    await render({ awardId: 5, uploads: true });

    component.add([file('a.pdf')]);
    fixture.detectChanges();

    expect(component.queue()[0]).toMatchObject({
      state: 'refused',
      problem: 'awards.documents.problems.duplicate-document',
    });
    expect(byTestId('documents-retry')).toBeNull();
    expect(service.list).toHaveBeenCalledTimes(2);

    component.dismiss(component.queue()[0]);
    expect(component.queue()).toHaveLength(0);
  });

  it.each([
    ['file-too-large', 413],
    ['unsupported-type', 400],
    ['content-mismatch', 400],
    ['document-limit', 409],
    ['malware-detected', 422],
  ])('ac5_server_refusal_%s_is_shown_by_its_code', async (type, status) => {
    service.upload.mockReturnValue(throwError(() => problem(type, status)));
    await render({ awardId: 5, uploads: true });

    component.add([file('a.pdf')]);

    expect(component.queue()[0].problem).toBe(`awards.documents.problems.${type}`);
  });

  it.each([
    ['storage-unavailable', 503],
    ['scanner-unavailable', 503],
    ['network', 0],
  ])('ac5_failure_%s_can_be_retried', async (type, status) => {
    service.upload
      .mockReturnValueOnce(throwError(() => problem(type, status)))
      .mockReturnValueOnce(stored(document()));
    await render({ awardId: 5, uploads: true });

    component.add([file('a.pdf')]);
    fixture.detectChanges();

    expect(component.queue()[0]).toMatchObject({
      state: 'failed',
      problem: 'awards.documents.problems.failed',
    });
    byTestId('documents-retry')?.click();

    expect(service.upload).toHaveBeenCalledTimes(2);
    expect(component.queue()).toHaveLength(0);
    expect(component.documents()).toHaveLength(1);
  });

  it('ac6_list_shows_name_type_size_and_kyiv_date', async () => {
    service.list.mockReturnValue(of([document(), document({ id: 2, size: 2048 })]));
    await render({ awardId: 5, uploads: true, removable: true });

    expect(all('document-name')[0].textContent?.trim()).toBe('диплом.pdf');
    expect(all('document-type')[0].textContent).toContain('awards.documents.types.CERTIFICATE');
    expect(component.sizeValue(2 * 1024 * 1024)).toBe('2');
    expect(component.sizeUnit(2 * 1024 * 1024)).toBe('awards.documents.megabytes');
    expect(component.sizeValue(1536 * 1024)).toBe('1,5');
    expect(component.sizeValue(2048)).toBe('2');
    expect(component.sizeUnit(2048)).toBe('awards.documents.kilobytes');
    expect(component.sizeValue(10)).toBe('1');
    expect(all('document-date')[0].textContent).toBe('02.10.2026');
  });

  it('ac6_english_sizes_use_a_decimal_point', async () => {
    language.current.mockReturnValue('en');
    await render({ awardId: 5 });

    expect(component.sizeValue(1536 * 1024)).toBe('1.5');
  });

  it('ac6_delete_asks_with_the_name_and_removes_the_document', async () => {
    service.list.mockReturnValue(of([document(), document({ id: 2 })]));
    service.remove.mockReturnValue(of(undefined));
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    const counts: number[] = [];
    await render({ awardId: 5, uploads: true, removable: true });
    component.countChange.subscribe((count) => counts.push(count));

    all('document-remove')[0].click();

    expect(dialog.open.mock.calls[0][1].data).toMatchObject({
      text: 'awards.documents.remove.text',
      params: { name: 'диплом.pdf' },
    });
    expect(service.remove).toHaveBeenCalledWith(1);
    expect(component.documents().map((item) => item.id)).toEqual([2]);
    expect(counts).toEqual([1]);
  });

  it('ac6_cancelled_delete_keeps_the_document', async () => {
    service.list.mockReturnValue(of([document()]));
    dialog.open.mockReturnValue({ afterClosed: () => of(false) });
    await render({ awardId: 5, removable: true });

    component.remove(document());

    expect(service.remove).not.toHaveBeenCalled();
    expect(component.documents()).toHaveLength(1);
  });

  it('ac6_delete_of_a_document_already_gone_drops_it_and_a_submitted_award_explains', async () => {
    service.list.mockReturnValue(of([document(), document({ id: 2 })]));
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    service.remove
      .mockReturnValueOnce(throwError(() => problem('document-not-found', 404)))
      .mockReturnValueOnce(throwError(() => problem('award-not-editable', 409)));
    await render({ awardId: 5, removable: true });

    component.remove(document());
    component.remove(document({ id: 2 }));

    expect(component.documents().map((item) => item.id)).toEqual([2]);
    expect(component.notice()).toBe('awards.documents.problems.award-not-editable');
  });

  it('ac6_image_opens_in_a_preview_dialog', async () => {
    const photo = document({ id: 3, fileName: 'фото.jpg', mimeType: 'image/jpeg' });
    service.list.mockReturnValue(of([photo]));
    service.download.mockReturnValue(of(new Blob(['x'], { type: 'image/jpeg' })));
    const createObjectURL = vi.fn(() => 'blob:preview');
    vi.stubGlobal('URL', { ...URL, createObjectURL, revokeObjectURL: vi.fn() });
    await render({ awardId: 5 });

    byTestId('document-name')?.click();

    expect(service.download).toHaveBeenCalledWith(3);
    expect(dialog.open).toHaveBeenCalledWith(DocumentPreviewDialogComponent, {
      data: { name: 'фото.jpg', url: 'blob:preview' },
      maxWidth: '90vw',
    });
    expect(byTestId('document-preview')).not.toBeNull();
    vi.unstubAllGlobals();
  });

  it('ac7_read_only_list_has_no_upload_and_no_delete', async () => {
    service.list.mockReturnValue(of([document()]));
    await render({ awardId: 5 });

    expect(byTestId('documents-drop')).toBeNull();
    expect(byTestId('document-remove')).toBeNull();
    expect(byTestId('document-download')).not.toBeNull();
  });

  it('ac7_award_without_documents_says_so', async () => {
    await render({ awardId: 5 });

    expect(byTestId('documents-empty')?.textContent).toContain('awards.documents.none');
  });

  it('ac9_download_saves_the_blob_under_the_document_name', async () => {
    service.list.mockReturnValue(of([document()]));
    service.download.mockReturnValue(of(new Blob(['%PDF-'])));
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: vi.fn(() => 'blob:file'),
      revokeObjectURL: vi.fn(),
    });
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined);
    await render({ awardId: 5 });

    byTestId('document-download')?.click();

    expect(service.download).toHaveBeenCalledWith(1);
    expect((click.mock.instances[0] as unknown as HTMLAnchorElement).download).toBe('диплом.pdf');
    click.mockRestore();
    vi.unstubAllGlobals();
  });

  it('ac9_document_no_longer_available_reloads_the_list', async () => {
    service.list.mockReturnValueOnce(of([document()])).mockReturnValueOnce(of([]));
    service.download.mockReturnValue(throwError(() => problem('document-not-found', 404)));
    await render({ awardId: 5 });

    component.download(document());
    fixture.detectChanges();

    expect(byTestId('documents-notice')?.textContent).toContain('awards.documents.problems.gone');
    expect(component.documents()).toHaveLength(0);
  });

  it('ac9_other_download_failures_keep_the_list', async () => {
    service.list.mockReturnValue(of([document()]));
    service.download.mockReturnValue(throwError(() => problem('unknown', 500)));
    await render({ awardId: 5 });

    component.preview(document());

    expect(component.notice()).toBe('awards.documents.problems.download-failed');
    expect(service.list).toHaveBeenCalledTimes(1);
  });

  it('list_of_an_award_no_longer_readable_says_so', async () => {
    service.list.mockReturnValueOnce(throwError(() => problem('award-not-found', 404)));
    await render({ awardId: 5 });
    expect(component.notice()).toBe('awards.documents.problems.gone');
  });

  it('list_failure_is_reported', async () => {
    service.list.mockReturnValueOnce(throwError(() => problem('unknown', 500)));
    await render({ awardId: 5 });
    expect(component.notice()).toBe('awards.documents.problems.list-failed');
  });

  it('ac11_enter_and_space_on_the_drop_zone_open_the_picker', async () => {
    await render({ awardId: 5, uploads: true });
    const input = byTestId('documents-input') as HTMLInputElement;
    const click = vi.spyOn(input, 'click').mockImplementation(() => undefined);
    const zone = byTestId('documents-drop') as HTMLElement;

    zone.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    zone.dispatchEvent(new KeyboardEvent('keydown', { key: ' ' }));

    expect(click).toHaveBeenCalledTimes(2);
  });

  it('dropped_files_are_queued_and_the_picker_is_cleared', async () => {
    service.upload.mockReturnValue(stored(document()));
    await render({ awardId: 5, uploads: true });
    const zone = byTestId('documents-drop') as HTMLElement;
    const over = new Event('dragover', { cancelable: true });
    zone.dispatchEvent(over);
    fixture.detectChanges();
    expect(component.dragging()).toBe(true);

    const drop = Object.assign(new Event('drop', { cancelable: true }), {
      dataTransfer: { files: [file('a.pdf')] },
    });
    zone.dispatchEvent(drop);

    expect(component.dragging()).toBe(false);
    expect(service.upload).toHaveBeenCalledTimes(1);

    const input = {
      files: [file('b.pdf')],
      value: 'C:\\fakepath\\b.pdf',
    } as unknown as HTMLInputElement;
    component.picked(input);
    expect(input.value).toBe('');
    component.add(null);
    expect(service.upload).toHaveBeenCalledTimes(2);
  });

  it('upload_without_an_award_or_a_way_to_save_one_fails', async () => {
    await render({ awardId: null, uploads: true });

    component.add([file('a.pdf')]);

    expect(component.queue()[0].state).toBe('failed');
  });
});
