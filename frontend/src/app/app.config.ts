import { provideHttpClient, withInterceptors, withInterceptorsFromDi } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  isDevMode,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
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
import { UpdatePromptService } from './core/pwa/update-prompt.service';
import { TranslocoHttpLoader } from './core/i18n/transloco.loader';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
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
      return Promise.all([
        inject(LanguageService).init(),
        inject(BrandService).init(),
        inject(AuthService).init(),
      ]);
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
