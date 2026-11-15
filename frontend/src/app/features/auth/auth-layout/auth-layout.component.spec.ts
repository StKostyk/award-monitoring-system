import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';

import { BrandService } from '../../../core/brand/brand.service';
import { AuthLayoutComponent } from './auth-layout.component';

@Component({
  imports: [AuthLayoutComponent],
  template: '<app-auth-layout><p data-testid="projected">form</p></app-auth-layout>',
})
class HostComponent {}

describe('AuthLayoutComponent', () => {
  function render(lang = 'uk'): HTMLElement {
    TestBed.configureTestingModule({
      imports: [
        HostComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            uk: { authLayout: { claim: 'Нагороди університету в одному місці.' } },
            en: { authLayout: { claim: 'University awards in one place.' } },
          },
          translocoConfig: { availableLangs: ['uk', 'en'], defaultLang: 'uk' },
          preloadLangs: true,
        }),
      ],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    TestBed.inject(BrandService).brand.set({
      id: 'chnu',
      name: { uk: 'Облік нагород ЧНУ', en: 'ChNU Awards' },
      organization: { uk: 'ЧНУ', en: 'ChNU' },
      logo: 'brand/chnu/logo.svg',
    });
    TestBed.inject(TranslocoService).setActiveLang(lang);
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('ac4_3 shows the brand panel with logo, name and claim beside the page', () => {
    const element = render();

    const panel = element.querySelector('[data-testid="auth-brand"]');
    expect(panel?.querySelector('img')?.getAttribute('src')).toBe('brand/chnu/logo.svg');
    expect(panel?.textContent).toContain('Облік нагород ЧНУ');
    expect(element.querySelector('[data-testid="auth-claim"]')?.textContent).toContain(
      'Нагороди університету',
    );
    expect(element.querySelector('.auth-layout__content [data-testid="projected"]')).not.toBeNull();
  });

  it('ac4_3 follows the active language', () => {
    const element = render('en');

    expect(element.querySelector('[data-testid="auth-brand"]')?.textContent).toContain(
      'ChNU Awards',
    );
    expect(element.querySelector('[data-testid="auth-claim"]')?.textContent).toContain(
      'University awards',
    );
  });
});
