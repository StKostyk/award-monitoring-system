import { HttpErrorResponse, HttpEventType, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Subject, of, throwError } from 'rxjs';
import { beforeEach, vi } from 'vitest';

import { LanguageService } from '../../../core/i18n/language.service';
import {
  AwardDocument,
  DocumentsService,
  MAX_DOCUMENT_SIZE,
} from '../award-documents/documents.service';
import { AwardsService } from '../awards.service';
import {
  AwardUploadStepComponent,
  UploadStepResult,
  titleFromFile,
} from './award-upload-step.component';

const stored = { id: 4, awardId: 9, fileName: 'gramota.pdf' } as AwardDocument;

function pdf(name = 'Грамота_МОН-2025.pdf', size = 1024): File {
  const file = new File(['%PDF'], name, { type: 'application/pdf' });
  Object.defineProperty(file, 'size', { value: size });
  return file;
}

describe('AwardUploadStepComponent', () => {
  let fixture: ComponentFixture<AwardUploadStepComponent>;
  let component: AwardUploadStepComponent;
  let results: UploadStepResult[];
  const awards = { create: vi.fn() };
  const documents = { upload: vi.fn() };

  beforeEach(async () => {
    awards.create.mockReset();
    documents.upload.mockReset();
    awards.create.mockReturnValue(of({ award: { id: 9 }, repeated: false }));
    await TestBed.configureTestingModule({
      imports: [
        AwardUploadStepComponent,
        NoopAnimationsModule,
        TranslocoTestingModule.forRoot({
          langs: { uk: {} },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [
        { provide: AwardsService, useValue: awards },
        { provide: DocumentsService, useValue: documents },
        { provide: LanguageService, useValue: { current: () => 'uk' } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AwardUploadStepComponent);
    component = fixture.componentInstance;
    results = [];
    component.done.subscribe((result) => results.push(result));
    fixture.detectChanges();
  });

  function element(testId: string): HTMLElement | null {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${testId}"]`);
  }

  it('ac5_3_saves_a_draft_titled_after_the_file_and_uploads_it_with_progress', () => {
    const events = new Subject<unknown>();
    documents.upload.mockReturnValue(events);

    component.send(pdf());
    events.next({ type: HttpEventType.UploadProgress, loaded: 512, total: 1024 });

    expect(awards.create).toHaveBeenCalledWith(
      expect.objectContaining({ titleUk: 'Грамота МОН 2025', title: null }),
      expect.any(String),
    );
    expect(documents.upload).toHaveBeenCalledWith(9, expect.any(File), 'CERTIFICATE');
    expect(element('upload-progress')).not.toBeNull();
    expect(component.progress()).toBe(50);
    expect(element('upload-skip')?.hasAttribute('disabled')).toBe(true);

    events.next(new HttpResponse({ body: stored }));

    expect(results).toEqual([{ awardId: 9, document: stored }]);
  });

  it('ac5_3_drop_sends_the_first_file', () => {
    documents.upload.mockReturnValue(of(new HttpResponse({ body: stored })));
    const event = {
      preventDefault: vi.fn(),
      dataTransfer: { files: [pdf()] },
    } as unknown as DragEvent;

    component.dropped(event);

    expect(event.preventDefault).toHaveBeenCalled();
    expect(results).toEqual([{ awardId: 9, document: stored }]);
  });

  it('ac5_3_skipping_opens_the_form_without_a_draft', () => {
    (element('upload-skip') as HTMLButtonElement).click();

    expect(results).toEqual([{ awardId: null, document: null }]);
    expect(awards.create).not.toHaveBeenCalled();
  });

  it('edge_a_file_outside_the_rules_is_refused_before_uploading', () => {
    component.send(pdf('scan.pdf', MAX_DOCUMENT_SIZE + 1));

    expect(element('upload-problem')?.textContent).toContain(
      'awards.documents.problems.file-too-large',
    );
    component.send(new File(['x'], 'notes.txt', { type: 'text/plain' }));

    expect(element('upload-problem')?.textContent).toContain(
      'awards.documents.problems.unsupported-type',
    );
    expect(awards.create).not.toHaveBeenCalled();
  });

  it('edge_a_scanner_outage_lets_the_person_continue_on_the_saved_draft', () => {
    documents.upload.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 503, error: { type: 'x' } })),
    );

    component.send(pdf());

    expect(element('upload-problem')?.textContent).toContain('awards.uploadStep.unavailable');
    expect(element('upload-skip')).toBeNull();
    (element('upload-continue') as HTMLButtonElement).click();

    expect(results).toEqual([{ awardId: 9, document: null }]);
  });

  it('edge_a_refused_upload_keeps_the_draft_for_the_next_file', () => {
    documents.upload.mockReturnValueOnce(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 422,
            error: { type: 'urn:awards:problem:content-mismatch' },
          }),
      ),
    );
    documents.upload.mockReturnValueOnce(of(new HttpResponse({ body: stored })));

    component.send(pdf());
    expect(element('upload-problem')?.textContent).toContain(
      'awards.documents.problems.content-mismatch',
    );
    component.send(pdf('other.pdf'));

    expect(awards.create).toHaveBeenCalledTimes(1);
    expect(results).toEqual([{ awardId: 9, document: stored }]);
  });

  it('edge_a_failed_save_shows_a_generic_problem', () => {
    awards.create.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

    component.send(pdf());

    expect(element('upload-problem')?.textContent).toContain('awards.documents.problems.failed');
    expect(element('upload-pick')).not.toBeNull();
  });

  it('ac5_3_titles_come_from_the_file_name', () => {
    expect(titleFromFile('Diploma_of-Honour.v2.png')).toBe('Diploma of Honour.v2');
    expect(titleFromFile('.pdf')).toBe('.pdf');
  });
});
