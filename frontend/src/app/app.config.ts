import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { apiErrorInterceptor } from './core/http/api-error.interceptor';
import { DEFAULT_LANGUAGE } from './core/i18n/language.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor, apiErrorInterceptor])),
    // Dates render as dd/MM/yyyy with Latin digits in both UI languages, so a
    // date never reads ambiguously on the factory floor.
    provideNativeDateAdapter(),
    { provide: MAT_DATE_LOCALE, useValue: 'en-GB' },
    provideTranslateService({
      lang: DEFAULT_LANGUAGE,
      fallbackLang: DEFAULT_LANGUAGE,
      loader: provideTranslateHttpLoader({
        prefix: '/i18n/',
        suffix: '.json',
        // Bypass the interceptor chain: apiErrorInterceptor injects TranslateService
        // to translate error codes, so routing the translation files themselves
        // through it is a circular dependency. Translation files need no auth token
        // and a failed load should not raise a toast in a language we cannot read.
        useHttpBackend: true,
      }),
    }),
  ],
};
