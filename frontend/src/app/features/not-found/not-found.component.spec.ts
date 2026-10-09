import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';

import { NotFoundComponent } from './not-found.component';

describe('NotFoundComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        NotFoundComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            uk: {
              notFound: {
                title: 'Сторінку не знайдено',
                text: 'Такої сторінки немає.',
                publicAchievements: 'Досягнення університету',
              },
            },
          },
          translocoConfig: { availableLangs: ['uk'], defaultLang: 'uk' },
        }),
      ],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('ac2_4_says_the_page_does_not_exist_and_leads_to_the_public_achievements', () => {
    const fixture = TestBed.createComponent(NotFoundComponent);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('h1')?.textContent).toContain('Сторінку не знайдено');
    expect(element.querySelector('[data-testid="not-found-public"]')?.getAttribute('href')).toBe(
      '/public/achievements',
    );
  });
});
