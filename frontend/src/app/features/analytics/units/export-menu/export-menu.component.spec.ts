import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Subject, throwError } from 'rxjs';
import { vi } from 'vitest';

import { languageStub, translations } from '../../analytics.testing';
import { UnitAnalyticsService } from '../unit-analytics.service';
import { ExportMenuComponent } from './export-menu.component';

describe('ExportMenuComponent', () => {
  let fixture: ComponentFixture<ExportMenuComponent>;
  let exportReport: ReturnType<typeof vi.fn>;
  let createUrl: ReturnType<typeof vi.spyOn>;
  let saved: string[];

  async function setUp(language: 'en' | 'uk' = 'en'): Promise<void> {
    exportReport = vi.fn();
    await TestBed.configureTestingModule({
      imports: [ExportMenuComponent, NoopAnimationsModule, translations(language)],
      providers: [
        { provide: UnitAnalyticsService, useValue: { exportReport } },
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
    fixture = TestBed.createComponent(ExportMenuComponent);
    fixture.componentRef.setInput('unitId', 9);
    fixture.componentRef.setInput('period', { from: 2021, to: 2025 });
    fixture.detectChanges();
  }

  function trigger(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('[data-testid="unit-export"]');
  }

  function choose(format: string): void {
    trigger().click();
    fixture.detectChanges();
    (document.querySelector(`[data-testid="unit-export-${format}"]`) as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  afterEach(() => vi.restoreAllMocks());

  it('ac3_6_offers_pdf_excel_and_csv', async () => {
    await setUp();
    trigger().click();
    fixture.detectChanges();

    const items = [...document.querySelectorAll('[data-testid^="unit-export-"]')];
    expect(items.map((item) => item.textContent?.trim())).toEqual([
      'PDF report',
      'Excel workbook',
      'CSV file',
    ]);
  });

  it('ac3_6_downloads_the_chosen_format_in_the_page_language_under_the_server_name', async () => {
    await setUp('uk');
    const body = new Blob(['report']);
    const responses = new Subject<HttpResponse<Blob>>();
    exportReport.mockReturnValue(responses);

    choose('xlsx');
    expect(exportReport).toHaveBeenCalledWith(9, { from: 2021, to: 2025 }, 'xlsx', 'uk');
    expect(trigger().disabled).toBe(true);

    responses.next(
      new HttpResponse({
        body,
        headers: new HttpHeaders({
          'Content-Disposition': 'attachment; filename="analytics-FMI-2021-2025.xlsx"',
        }),
      }),
    );
    fixture.detectChanges();

    expect(createUrl).toHaveBeenCalledWith(body);
    expect(saved).toEqual(['analytics-FMI-2021-2025.xlsx']);
    expect(trigger().disabled).toBe(false);
  });

  it('ac3_6_falls_back_to_a_name_of_the_unit_and_format', async () => {
    await setUp();
    const responses = new Subject<HttpResponse<Blob>>();
    exportReport.mockReturnValue(responses);

    choose('csv');
    responses.next(new HttpResponse({ body: new Blob() }));

    expect(saved).toEqual(['analytics-9.csv']);
  });

  it('ac3_6_reports_a_failed_export_and_keeps_the_menu', async () => {
    await setUp();
    exportReport.mockReturnValue(throwError(() => new Error('offline')));

    choose('pdf');

    expect(saved).toEqual([]);
    const failed = fixture.nativeElement.querySelector('[data-testid="unit-export-failed"]');
    expect(failed.textContent).toContain('The report could not be exported.');
    expect(trigger().disabled).toBe(false);
  });

  it('ac3_6_is_disabled_when_the_page_has_no_report', async () => {
    await setUp();
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();
    expect(trigger().disabled).toBe(true);

    fixture.componentRef.setInput('disabled', false);
    fixture.componentRef.setInput('unitId', null);
    fixture.detectChanges();
    expect(trigger().disabled).toBe(true);
  });
});
