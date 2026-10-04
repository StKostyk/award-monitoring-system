import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';

import { BRAND_URL, BrandService, NEUTRAL_BRAND } from './brand.service';

const chnu = {
  id: 'chnu',
  name: { uk: 'Облік нагород ЧНУ', en: 'ChNU Awards' },
  organization: { uk: 'Чернівецький університет', en: 'Chernivtsi University' },
  logo: 'brand/chnu/logo.svg',
};

describe('BrandService', () => {
  let service: BrandService;
  let http: HttpTestingController;
  const root = document.documentElement;

  beforeEach(() => {
    root.classList.remove('brand-chnu', 'brand-neutral');
    TestBed.configureTestingModule({
      imports: [
        TranslocoTestingModule.forRoot({
          langs: { uk: {}, en: {} },
          translocoConfig: { availableLangs: ['uk', 'en'], defaultLang: 'uk' },
        }),
      ],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BrandService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function load(answer: (request: ReturnType<HttpTestingController['expectOne']>) => void) {
    const done = service.init();
    answer(http.expectOne(BRAND_URL));
    await done;
  }

  it('ac1_ac3_applies_the_chnu_brand_from_the_brand_file', async () => {
    await load((request) => request.flush(chnu));

    expect(root.classList).toContain('brand-chnu');
    expect(root.classList).not.toContain('brand-neutral');
    expect(service.brand()).toEqual(chnu);
    expect(TestBed.inject(Title).getTitle()).toBe('Облік нагород ЧНУ');
  });

  it('ac3_follows_the_language_in_the_tab_title_and_texts', async () => {
    await load((request) => request.flush(chnu));
    TestBed.inject(TranslocoService).setActiveLang('en');

    expect(TestBed.inject(Title).getTitle()).toBe('ChNU Awards');
    expect(service.text(chnu.organization)).toBe('Chernivtsi University');
  });

  it('ac2_falls_back_to_the_neutral_brand_when_the_file_is_missing', async () => {
    await load((request) => request.flush('', { status: 404, statusText: 'Not Found' }));

    expect(root.classList).toContain('brand-neutral');
    expect(service.brand()).toEqual(NEUTRAL_BRAND);
  });

  it('ac2_falls_back_to_the_neutral_brand_for_an_unknown_id', async () => {
    await load((request) => request.flush({ ...chnu, id: 'oxford' }));

    expect(root.classList).toContain('brand-neutral');
  });

  for (const logo of [
    'https://example.org/logo.svg',
    '//example.org/logo.svg',
    '/logo.svg',
    '../logo.svg',
  ]) {
    it(`ac2_rejects_a_brand_with_the_logo_${logo}`, async () => {
      await load((request) => request.flush({ ...chnu, logo }));

      expect(service.brand()).toEqual(NEUTRAL_BRAND);
    });
  }

  it('ac2_rejects_a_brand_without_both_languages', async () => {
    await load((request) => request.flush({ ...chnu, name: { uk: 'Облік' } }));

    expect(service.brand()).toEqual(NEUTRAL_BRAND);
  });
});
