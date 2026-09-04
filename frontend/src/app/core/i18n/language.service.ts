import { DOCUMENT, Injectable, computed, effect, inject, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

import { localStore } from '../storage/local-store';

export type AppLanguage = 'ar' | 'en';

export const DEFAULT_LANGUAGE: AppLanguage = 'ar';
export const SUPPORTED_LANGUAGES: readonly AppLanguage[] = ['ar', 'en'];

const STORAGE_KEY = 'apparel.language';

/**
 * Owns the active UI language and the matching text direction.
 *
 * Arabic is the default. The chosen language is persisted per browser and the
 * `lang`/`dir` attributes on `<html>` are kept in sync so CSS logical
 * properties, native form controls and Angular Material all flip together.
 */
@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly translate = inject(TranslateService);
  private readonly document = inject(DOCUMENT);

  private readonly currentLanguage = signal<AppLanguage>(readStoredLanguage());

  readonly language = this.currentLanguage.asReadonly();
  readonly direction = computed<'rtl' | 'ltr'>(() =>
    this.currentLanguage() === 'ar' ? 'rtl' : 'ltr',
  );
  readonly isRtl = computed(() => this.direction() === 'rtl');

  constructor() {
    effect(() => {
      const language = this.currentLanguage();
      this.translate.use(language);

      const root = this.document.documentElement;
      root.setAttribute('lang', language);
      root.setAttribute('dir', this.direction());

      localStore.set(STORAGE_KEY, language);
    });
  }

  use(language: AppLanguage): void {
    this.currentLanguage.set(language);
  }

  toggle(): void {
    this.currentLanguage.update((current) => (current === 'ar' ? 'en' : 'ar'));
  }
}

function readStoredLanguage(): AppLanguage {
  const stored = localStore.get(STORAGE_KEY);
  return stored && SUPPORTED_LANGUAGES.includes(stored as AppLanguage)
    ? (stored as AppLanguage)
    : DEFAULT_LANGUAGE;
}
