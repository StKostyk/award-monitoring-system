import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { languageStub, translations } from '../../analytics.testing';
import { ComplianceSummary } from '../university-analytics.service';
import { complianceSummary } from '../university-analytics.testing';
import { CompliancePanelComponent } from './compliance-panel.component';

describe('CompliancePanelComponent', () => {
  let fixture: ComponentFixture<CompliancePanelComponent>;
  let refreshes: number;

  function render(
    compliance: ComplianceSummary | null,
    options: { loading?: boolean; problem?: string | null; language?: 'en' | 'uk' } = {},
  ): HTMLElement {
    const language = options.language ?? 'en';
    TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, translations(language)],
      providers: [languageStub(language).provider],
    });
    fixture = TestBed.createComponent(CompliancePanelComponent);
    fixture.componentRef.setInput('compliance', compliance);
    fixture.componentRef.setInput('loading', options.loading ?? false);
    fixture.componentRef.setInput('problem', options.problem ?? null);
    refreshes = 0;
    fixture.componentInstance.refresh.subscribe(() => refreshes++);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function text(element: HTMLElement, testId: string): string {
    return element.querySelector(`[data-testid="${testId}"]`)?.textContent?.trim() ?? '';
  }

  it('ac2_7_shows_needs_attention_with_one_sentence_per_reason_and_the_tiles', () => {
    const element = render(complianceSummary());

    expect(text(element, 'compliance-status')).toContain('Needs attention');
    expect(element.querySelectorAll('[data-testid="compliance-reasons"] li')).toHaveLength(2);
    expect(text(element, 'compliance-reason-ERASURE_OVERDUE')).toBe(
      'Some erasure requests are past their due date.',
    );
    expect(text(element, 'compliance-tile-notice')).toContain('150 / 200');
    expect(text(element, 'compliance-tile-notice')).toContain('75 %');
    expect(text(element, 'compliance-tile-consent')).toContain('25 %');
    expect(text(element, 'compliance-tile-erasure-pending')).toContain('3');
    expect(text(element, 'compliance-tile-erasure-postponed')).toContain('1');
    expect(text(element, 'compliance-tile-legal-holds')).toContain('2');
    expect(text(element, 'compliance-tile-retention')).toContain('Completed');
    expect(text(element, 'compliance-tile-reviews-overdue')).toContain('2');
    expect(text(element, 'compliance-as-of')).toContain('As of');
  });

  it('ac2_7_shows_everything_in_order_and_no_run_yet', () => {
    const element = render({
      ...complianceSummary(),
      status: 'OK',
      attention: [],
      lastRetentionRun: null,
      privacyNotice: { version: '1.2', accounts: 0, acknowledged: 0 },
    });

    expect(text(element, 'compliance-status')).toContain('Everything is in order');
    expect(element.querySelector('[data-testid="compliance-reasons"]')).toBeNull();
    expect(text(element, 'compliance-tile-retention')).toContain('No run yet');
    expect(text(element, 'compliance-tile-notice')).not.toContain('%');
  });

  it('ac2_7_refresh_is_disabled_while_loading', () => {
    const element = render(complianceSummary(), { loading: true });
    const button = element.querySelector('[data-testid="compliance-refresh"]') as HTMLButtonElement;

    expect(button.disabled).toBe(true);
    expect(element.querySelector('[data-testid="compliance-loading"]')).not.toBeNull();
  });

  it('ac2_7_a_failed_load_shows_its_own_error_with_retry', () => {
    const element = render(null, { problem: 'unknown' });

    expect(text(element, 'compliance-error')).toContain(
      'The compliance summary could not be loaded.',
    );
    (element.querySelector('[data-testid="compliance-error"] button') as HTMLButtonElement).click();
    (element.querySelector('[data-testid="compliance-refresh"]') as HTMLButtonElement).click();

    expect(refreshes).toBe(2);
  });

  it('ac2_9_reads_in_ukrainian', () => {
    const element = render(complianceSummary(), { language: 'uk' });

    expect(text(element, 'compliance-status')).toContain('Потребує уваги');
    expect(text(element, 'compliance-refresh')).toContain('Оновити');
  });
});
