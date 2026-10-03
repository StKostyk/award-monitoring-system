import { HttpEventType, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { DocumentsService, extensionOf, isAcceptedFile } from './documents.service';

describe('DocumentsService', () => {
  let service: DocumentsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DocumentsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists_the_documents_of_an_award', () => {
    service.list(5).subscribe();
    expect(
      http.expectOne((request) => request.url.endsWith('/api/v1/awards/5/documents')).request
        .method,
    ).toBe('GET');
  });

  it('ac2_4_uploads_a_multipart_form_with_progress_events', () => {
    const events: HttpEventType[] = [];
    service
      .upload(5, new File(['%PDF-'], 'диплом.pdf'), 'CERTIFICATE')
      .subscribe((event) => events.push(event.type));

    const request = http.expectOne((call) => call.url.endsWith('/api/v1/awards/5/documents'));
    const body = request.request.body as FormData;
    expect(request.request.method).toBe('POST');
    expect(request.request.reportProgress).toBe(true);
    expect((body.get('file') as File).name).toBe('диплом.pdf');
    expect(body.get('type')).toBe('CERTIFICATE');
    request.event({ type: HttpEventType.UploadProgress, loaded: 1, total: 2 });
    request.flush({ id: 1 });

    expect(events).toContain(HttpEventType.UploadProgress);
    expect(events).toContain(HttpEventType.Response);
  });

  it('ac2_9_downloads_a_blob_with_the_token_header_not_in_the_url', () => {
    service.download(7).subscribe();
    const request = http.expectOne((call) => call.url.endsWith('/api/v1/documents/7'));
    expect(request.request.responseType).toBe('blob');
    expect(request.request.urlWithParams).not.toContain('token');
    request.flush(new Blob(['x']));
  });

  it('removes_a_document', () => {
    service.remove(7).subscribe();
    expect(http.expectOne((call) => call.url.endsWith('/api/v1/documents/7')).request.method).toBe(
      'DELETE',
    );
  });

  it('ac2_3_accepts_by_media_type_or_extension', () => {
    expect(isAcceptedFile(new File(['x'], 'scan', { type: 'application/pdf' }))).toBe(true);
    expect(isAcceptedFile(new File(['x'], 'photo.JPEG'))).toBe(true);
    expect(isAcceptedFile(new File(['x'], 'photo.webp'))).toBe(true);
    expect(isAcceptedFile(new File(['x'], 'IMG.heic', { type: 'image/heic' }))).toBe(false);
    expect(isAcceptedFile(new File(['x'], 'notes'))).toBe(false);
    expect(extensionOf('archive.tar.GZ')).toBe('gz');
    expect(extensionOf('README')).toBe('');
  });
});
