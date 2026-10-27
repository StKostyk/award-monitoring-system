import { DestroyRef, Injectable, inject } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import {
  BehaviorSubject,
  Observable,
  combineLatest,
  distinctUntilChanged,
  map,
  of,
  switchMap,
} from 'rxjs';

import { Brand, BrandService } from '../brand/brand.service';

const SEPARATOR = ' — ';

/**
 * Titles every page "page — brand" in the interface language: the route `title` is a translation key, and the
 * title follows a language switch and the brand of the deployment. Routes without a title show the brand name.
 */
@Injectable({ providedIn: 'root' })
export class PageTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly transloco = inject(TranslocoService);
  private readonly brands = inject(BrandService);
  private readonly key = new BehaviorSubject<string | null>(null);

  constructor() {
    super();
    combineLatest([
      this.key.pipe(distinctUntilChanged()),
      this.transloco.langChanges$,
      toObservable(this.brands.brand),
    ])
      .pipe(
        switchMap(([key, lang, brand]) => {
          const page: Observable<string | null> = key
            ? this.transloco.selectTranslate<string>(key, {}, lang)
            : of(null);
          return page.pipe(map((text) => this.compose(text, lang, brand)));
        }),
        distinctUntilChanged(),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((text) => this.title.setTitle(text));
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.key.next(this.buildTitle(snapshot) ?? null);
  }

  private compose(page: string | null, lang: string, brand: Brand): string {
    const name = this.brands.text(brand.name, lang);
    return page ? `${page}${SEPARATOR}${name}` : name;
  }
}
