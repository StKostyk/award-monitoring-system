import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { AwardImportService, ImportMapping } from './award-import.service';

function read(blob: Blob): Promise<string> {
  return new Promise((resolve) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.readAsText(blob);
  });
}

describe('AwardImportService', () => {
  let service: AwardImportService;
  let http: HttpTestingController;
  const file = new File(['a'], 'awards.csv', { type: 'text/csv' });
  const mapping: ImportMapping = {
    columns: { FULL_NAME: 0 },
    departmentId: 41,
    defaultLevel: null,
    defaultAwardingOrganization: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AwardImportService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('ac5_1_previews_without_a_mapping_part_when_the_columns_are_guessed', () => {
    service.preview(file, null).subscribe();

    const request = http.expectOne(`${environment.apiUrl}/award-imports/preview`);
    const body = request.request.body as FormData;
    expect(request.request.method).toBe('POST');
    expect(body.get('file')).toBe(file);
    expect(body.has('mapping')).toBe(false);
    request.flush({});
  });

  it('ac5_2_sends_the_mapping_as_json_with_the_checksum', async () => {
    service.importRows(file, mapping, 'sum').subscribe();

    const request = http.expectOne(`${environment.apiUrl}/award-imports`);
    const body = request.request.body as FormData;
    const part = body.get('mapping') as Blob;
    expect(part.type).toBe('application/json');
    expect(JSON.parse(await read(part))).toEqual(mapping);
    expect(body.get('checksum')).toBe('sum');
    request.flush({ imported: 0, peopleAdded: 0, skipped: 0 });
  });

  it('ac5_1_reads_the_template_as_a_blob', () => {
    service.template().subscribe();

    const request = http.expectOne(`${environment.apiUrl}/award-imports/template`);
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['x']));
  });
});
