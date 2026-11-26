import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';

import { ForbiddenComponent } from './forbidden.component';

describe('ForbiddenComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ForbiddenComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            uk: {
              admin: {
                forbidden: {
                  title: 'Доступ заборонено',
                  text: 'У вас немає прав для цієї сторінки.',
                  home: 'На головну',
                },
              },
            },
          },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('ac2_10_explains_that_the_page_is_closed_and_offers_the_way_back', () => {
    const fixture = TestBed.createComponent(ForbiddenComponent);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('[data-testid="forbidden-card"]')?.textContent).toContain(
      'Доступ заборонено',
    );
    expect(element.querySelector('[data-testid="forbidden-home"]')?.getAttribute('href')).toBe('/');
  });
});
