import { Component, WritableSignal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { Router, TitleStrategy, provideRouter } from '@angular/router';
import { TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';

import { BrandService } from '../brand/brand.service';
import { PageTitleStrategy } from './page-title.strategy';

@Component({ template: '' })
class PageComponent {}

const brand = { uk: 'Облік нагород ЧНУ', en: 'ChNU Awards' };

describe('PageTitleStrategy', () => {
  let router: Router;
  let title: Title;
  let brandSignal: WritableSignal<{ name: typeof brand }>;

  beforeEach(() => {
    brandSignal = signal({ name: brand });
    TestBed.configureTestingModule({
      imports: [
        TranslocoTestingModule.forRoot({
          langs: {
            uk: { titles: { awards: 'Мої нагороди', award: 'Нагорода' } },
            en: { titles: { awards: 'My awards', award: 'Award' } },
          },
          translocoConfig: { availableLangs: ['uk', 'en'], defaultLang: 'uk' },
          preloadLangs: true,
        }),
      ],
      providers: [
        provideRouter([
          { path: 'awards', title: 'titles.awards', component: PageComponent },
          { path: 'awards/:id', title: 'titles.award', component: PageComponent },
          { path: 'untitled', component: PageComponent },
        ]),
        { provide: TitleStrategy, useExisting: PageTitleStrategy },
        {
          provide: BrandService,
          useValue: {
            brand: brandSignal,
            text: (text: typeof brand, lang: string) => (lang === 'en' ? text.en : text.uk),
          },
        },
      ],
    });
    router = TestBed.inject(Router);
    title = TestBed.inject(Title);
  });

  async function go(url: string): Promise<void> {
    await router.navigateByUrl(url);
    TestBed.tick();
  }

  it('ac1_2_titles_a_page_with_its_name_and_the_brand', async () => {
    await go('/awards');

    expect(title.getTitle()).toBe('Мої нагороди — Облік нагород ЧНУ');
  });

  it('ac1_2_a_record_page_is_titled_by_its_kind', async () => {
    await go('/awards/42');

    expect(title.getTitle()).toBe('Нагорода — Облік нагород ЧНУ');
  });

  it('ac1_2_follows_a_language_switch', async () => {
    await go('/awards');

    TestBed.inject(TranslocoService).setActiveLang('en');

    expect(title.getTitle()).toBe('My awards — ChNU Awards');
  });

  it('ac1_2_follows_the_brand_of_the_deployment', async () => {
    await go('/awards');

    brandSignal.set({ name: { uk: 'Облік нагород', en: 'Awards' } });
    TestBed.tick();

    expect(title.getTitle()).toBe('Мої нагороди — Облік нагород');
  });

  it('ac1_2_a_route_without_a_title_shows_the_brand', async () => {
    await go('/untitled');

    expect(title.getTitle()).toBe('Облік нагород ЧНУ');
  });
});
