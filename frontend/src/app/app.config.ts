import { provideHttpClient, withInterceptors, withInterceptorsFromDi } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  isDevMode,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { provideEffects } from '@ngrx/effects';
import { provideStore } from '@ngrx/store';
import { provideStoreDevtools } from '@ngrx/store-devtools';
import { provideOAuthClient } from 'angular-oauth2-oidc';

import { environment } from '../environments/environment';
import { routes } from './app.routes';
import { unauthorizedInterceptor } from './core/api/unauthorized.interceptor';
import { AuthService } from './core/auth/auth.service';
import { LanguageService } from './core/i18n/language.service';
import { TranslocoHttpLoader } from './core/i18n/transloco.loader';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptorsFromDi(), withInterceptors([unauthorizedInterceptor])),
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
      inject(LanguageService).init();
      return inject(AuthService).init();
    }),
    provideStore(),
    provideEffects(),
    provideStoreDevtools({ maxAge: 25, logOnly: !isDevMode() }),
  ],
};
