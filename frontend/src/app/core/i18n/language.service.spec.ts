import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';

import { localStore } from '../storage/local-store';
import { LanguageService } from './language.service';

describe('LanguageService', () => {
  beforeEach(() => {
    localStore.clear();
    TestBed.configureTestingModule({ providers: [provideTranslateService({ lang: 'ar' })] });
  });

  it('defaults to Arabic in RTL', () => {
    const service = TestBed.inject(LanguageService);
    expect(service.language()).toBe('ar');
    expect(service.direction()).toBe('rtl');
  });

  it('switches direction and the document attributes with the language', () => {
    const service = TestBed.inject(LanguageService);
    TestBed.tick();

    service.use('en');
    TestBed.tick();

    expect(service.direction()).toBe('ltr');
    expect(document.documentElement.getAttribute('dir')).toBe('ltr');
    expect(document.documentElement.getAttribute('lang')).toBe('en');
  });

  it('remembers the chosen language', () => {
    TestBed.inject(LanguageService).use('en');
    TestBed.tick();
    expect(localStore.get('apparel.language')).toBe('en');
  });
});
