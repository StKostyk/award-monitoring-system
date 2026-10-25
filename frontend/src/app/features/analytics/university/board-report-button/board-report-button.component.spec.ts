import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Subject, throwError } from 'rxjs';
import { vi } from 'vitest';

import { languageStub, translations } from '../../analytics.testing';
import { UniversityAnalyticsService } from '../university-analytics.service';
import { BoardReportButtonComponent } from './board-report-button.component';

describe('BoardReportButtonComponent', () => {
  let fixture: ComponentFixture<BoardReportButtonComponent>;
  let exportReport: ReturnType<typeof vi.fn>;
  let createUrl: ReturnType<typeof vi.spyOn>;
  let saved: string[];

  async function setUp(language: 'en' | 'uk' = 'en'): Promise<void> {
    exportReport = vi.fn();
    await TestBed.configureTestingModule({
      imports: [BoardReportButtonComponent, NoopAnimationsModule, translations(language)],
      providers: [
        { provide: UniversityAnalyticsService, useValue: { exportReport } },
        languageStub(language).provider,
      ],
    }).compileComponents();
    saved = [];
    createUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:report');
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      saved.push(this.download);
    });
    fixture = TestBed.createComponent(BoardReportButtonComponent);
    fixture.componentRef.setInput('period', { from: 2021, to: 2025 });
    fixture.detectChanges();
  }

  function button(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('[data-testid="board-report"]');
  }

  function click(): void {
    button().click();
    fixture.detectChanges();
  }

  afterEach(() => vi.restoreAllMocks());

  it('ac3_5_downloads_the_period_in_the_page_language_under_the_server_name', async () => {
    await setUp('uk');
    const body = new Blob(['%PDF']);
    const responses = new Subject<HttpResponse<Blob>>();
    exportReport.mockReturnValue(responses);

    expect(button().textContent?.trim()).toBe('Звіт для ради');
    click();
    expect(exportReport).toHaveBeenCalledWith({ from: 2021, to: 2025 }, 'uk');
    expect(button().disabled).toBe(true);

    responses.next(
      new HttpResponse({
        body,
        headers: new HttpHeaders({
          'Content-Disposition': 'attachment; filename="board-report-CHNU-2021-2025.pdf"',
        }),
      }),
    );
    fixture.detectChanges();

    expect(createUrl).toHaveBeenCalledWith(body);
    expect(saved).toEqual(['board-report-CHNU-2021-2025.pdf']);
    expect(button().disabled).toBe(false);
  });

  it('ac3_5_falls_back_to_a_generic_name', async () => {
    await setUp();
    const responses = new Subject<HttpResponse<Blob>>();
    exportReport.mockReturnValue(responses);

    click();
    responses.next(new HttpResponse({ body: new Blob() }));

    expect(saved).toEqual(['board-report.pdf']);
  });

  it('ac3_5_reports_a_failed_download_and_keeps_the_button', async () => {
    await setUp();
    exportReport.mockReturnValue(throwError(() => new Error('offline')));

    click();

    expect(saved).toEqual([]);
    const failed = fixture.nativeElement.querySelector('[data-testid="board-report-failed"]');
    expect(failed.textContent).toContain('The report could not be exported.');
    expect(button().disabled).toBe(false);
  });

  it('ac3_5_sends_one_request_for_a_double_click', async () => {
    await setUp();
    exportReport.mockReturnValue(new Subject<HttpResponse<Blob>>());

    click();
    click();

    expect(exportReport).toHaveBeenCalledTimes(1);
  });

  it('ac3_5_is_disabled_when_the_page_has_no_report', async () => {
    await setUp();
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();

    expect(button().disabled).toBe(true);
  });
});
