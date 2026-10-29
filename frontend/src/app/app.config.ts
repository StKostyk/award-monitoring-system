import { provideHttpClient, withInterceptors, withInterceptorsFromDi } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  isDevMode,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { AnimationsConfig, MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TitleStrategy, provideRouter } from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';
import { provideTransloco } from '@jsverse/transloco';
import { provideEffects } from '@ngrx/effects';
import { provideStore } from '@ngrx/store';
import { provideStoreDevtools } from '@ngrx/store-devtools';
import { provideOAuthClient } from 'angular-oauth2-oidc';

import { environment } from '../environments/environment';
import { routes } from './app.routes';
import { unauthorizedInterceptor } from './core/api/unauthorized.interceptor';
import { AuthService } from './core/auth/auth.service';
import { BrandService } from './core/brand/brand.service';
import { ColorSchemeService } from './core/brand/color-scheme.service';
import { connectivityInterceptor } from './core/connectivity/connectivity.interceptor';
import { provideIsoDateAdapter } from './core/i18n/iso-date-adapter';
import { LanguageService } from './core/i18n/language.service';
import { PageTitleStrategy } from './core/i18n/page-title.strategy';
import { UpdatePromptService } from './core/pwa/update-prompt.service';
import { TranslocoHttpLoader } from './core/i18n/transloco.loader';
import { prepareDateFormats } from './shared/date-format';
import { reducedMotion } from './shared/touch';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    { provide: TitleStrategy, useExisting: PageTitleStrategy },
    {
      provide: MATERIAL_ANIMATIONS,
      useFactory: (): AnimationsConfig => ({ animationsDisabled: reducedMotion() }),
    },
    provideHttpClient(
      withInterceptorsFromDi(),
      withInterceptors([connectivityInterceptor, unauthorizedInterceptor]),
    ),
    provideOAuthClient({
      resourceServer: { allowedUrls: [environment.apiUrl], sendAccessToken: true },
    }),
    provideTransloco({
      config: {
        availableLangs: ['uk', 'en'],
        defaultLang: 'uk',
        fallbackLang: 'uk',
        reRenderOnLangChange: true,
        prodMode: environment.production,
      },
      loader: TranslocoHttpLoader,
    }),
    provideAppInitializer(() => {
      inject(ColorSchemeService).init();
      inject(UpdatePromptService).init();
      const ready = Promise.all([
        inject(LanguageService).init(),
        inject(BrandService).init(),
        inject(AuthService).init(),
      ]);
      prepareDateFormats();
      return ready;
    }),
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
    provideIsoDateAdapter(),
    provideStore(),
    provideEffects(),
    provideStoreDevtools({ maxAge: 25, logOnly: !isDevMode() }),
  ],
};
