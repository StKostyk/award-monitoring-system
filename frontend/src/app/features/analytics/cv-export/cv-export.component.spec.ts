import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Subject, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AnalyticsService } from '../analytics.service';
import { languageStub, translations } from '../analytics.testing';
import { CvExportComponent } from './cv-export.component';

describe('CvExportComponent', () => {
  let fixture: ComponentFixture<CvExportComponent>;
  let exportCv: ReturnType<typeof vi.fn>;
  let saved: string[];

  async function setUp(language: 'en' | 'uk' = 'en'): Promise<void> {
    exportCv = vi.fn();
    await TestBed.configureTestingModule({
      imports: [CvExportComponent, NoopAnimationsModule, translations(language)],
      providers: [
        { provide: AnalyticsService, useValue: { exportCv } },
        languageStub(language).provider,
      ],
    }).compileComponents();
    saved = [];
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:cv');
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      saved.push(this.download);
    });
    fixture = TestBed.createComponent(CvExportComponent);
    fixture.detectChanges();
  }

  function trigger(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('[data-testid="cv-export"]');
  }

  function choose(format: 'pdf' | 'text'): void {
    trigger().click();
    fixture.detectChanges();
    (document.querySelector(`[data-testid="cv-export-${format}"]`) as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  afterEach(() => vi.restoreAllMocks());

  it('ac4_3_downloads_the_chosen_format_in_the_page_language_under_the_server_name', async () => {
    await setUp('uk');
    const responses = new Subject<HttpResponse<Blob>>();
    exportCv.mockReturnValue(responses);

    expect(trigger().getAttribute('aria-label')).toBe('Експорт для резюме');
    choose('text');

    expect(exportCv).toHaveBeenCalledWith('text', 'uk');
    expect(trigger().disabled).toBe(true);
    responses.next(
      new HttpResponse({
        body: new Blob(['2026']),
        headers: new HttpHeaders({
          'Content-Disposition': 'attachment; filename="awards-cv-2026-11-17.txt"',
        }),
      }),
    );
    fixture.detectChanges();

    expect(saved).toEqual(['awards-cv-2026-11-17.txt']);
    expect(trigger().disabled).toBe(false);
  });

  it('ac4_3_falls_back_to_a_name_by_format', async () => {
    await setUp();
    const responses = new Subject<HttpResponse<Blob>>();
    exportCv.mockReturnValue(responses);
    choose('pdf');
    responses.next(new HttpResponse({ body: new Blob() }));

    expect(exportCv).toHaveBeenCalledWith('pdf', 'en');
    expect(saved).toEqual(['awards-cv.pdf']);
  });

  it('ac4_4_reports_a_refused_download_and_keeps_the_menu', async () => {
    await setUp();
    exportCv.mockReturnValue(throwError(() => new Error('429')));
    choose('pdf');

    expect(saved).toEqual([]);
    expect(
      fixture.nativeElement.querySelector('[data-testid="cv-export-failed"]').textContent,
    ).toContain('could not be exported');
    expect(trigger().disabled).toBe(false);
  });

  it('ac4_5_is_disabled_without_approved_awards', async () => {
    await setUp();
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();

    expect(trigger().disabled).toBe(true);
  });
});
