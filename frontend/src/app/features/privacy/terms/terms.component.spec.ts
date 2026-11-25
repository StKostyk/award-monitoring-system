import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { languageStub, translations } from '../../analytics/analytics.testing';
import { TERMS_VERSION, TermsComponent } from './terms.component';

describe('TermsComponent', () => {
  let fixture: ComponentFixture<TermsComponent>;
  let http: HttpTestingController;
  const language = languageStub('uk');

  beforeEach(() => {
    language.current.set('uk');
    TestBed.configureTestingModule({
      imports: [TermsComponent, translations('uk')],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        language.provider,
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TermsComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function element(testId: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  it('ac10_2_shows_the_terms_in_the_interface_language_with_the_privacy_link', () => {
    http.expectOne(`/terms/${TERMS_VERSION}/uk.html`).flush('<h2>1. Про систему</h2>');
    fixture.detectChanges();

    expect(element('terms')?.querySelector('h1')?.textContent).toContain('Умови користування');
    expect(element('terms-text')?.textContent).toContain('Про систему');
    expect(element('terms-privacy-link')?.getAttribute('href')).toBe('/privacy-notice');
  });

  it('ac10_2_reads_the_other_language_when_it_changes', () => {
    http.expectOne(`/terms/${TERMS_VERSION}/uk.html`).flush('<h2>1. Про систему</h2>');
    language.current.set('en');
    fixture.detectChanges();

    http.expectOne(`/terms/${TERMS_VERSION}/en.html`).flush('<h2>1. About the system</h2>');
    fixture.detectChanges();

    expect(element('terms-text')?.textContent).toContain('About the system');
  });

  it('ac10_2_says_so_when_the_text_cannot_be_loaded', () => {
    http
      .expectOne(`/terms/${TERMS_VERSION}/uk.html`)
      .flush(null, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(element('terms-failed')?.textContent).toContain('Не вдалося завантажити');
  });
});
